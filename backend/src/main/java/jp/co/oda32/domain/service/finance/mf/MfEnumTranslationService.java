package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.repository.finance.MMfEnumTranslationRepository;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MF API 英語 enum → 既存 mf_account_master の日本語値 翻訳辞書管理。
 * <p>
 * 初回は {@link #autoSeed(Integer)} で既存 mf_account_master と MF API /accounts を突合し、
 * account_name 一致行から (MF.financial_statement_type, mf_account_master.report_name) /
 * (MF.category, mf_account_master.category) のペアを自動登録する。
 * <p>
 * 以降は画面から {@link #upsertAll(List, Integer)} で編集可能。未知 enum は sync 時に
 * 自動追加 (japanese_name=英語のまま + 警告) して後で手修正できる。
 *
 * @since 2026/04/21
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfEnumTranslationService {

    public static final String KIND_FINANCIAL_STATEMENT = "FINANCIAL_STATEMENT";
    public static final String KIND_CATEGORY = "CATEGORY";

    private final MMfEnumTranslationRepository translationRepository;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;

    /** 有効な翻訳全件（画面表示 + sync で使用）。 */
    public List<MMfEnumTranslation> findAll() {
        return translationRepository.findAllByDelFlgOrderByEnumKindAscEnglishCodeAsc("0");
    }

    /** {@code (kind, englishCode) -> japaneseName} のマップを返す (sync 時に O(1) lookup 用)。 */
    public Map<String, String> buildLookup() {
        Map<String, String> map = new HashMap<>();
        for (MMfEnumTranslation t : findAll()) {
            map.put(t.getEnumKind() + "|" + t.getEnglishCode(), t.getJapaneseName());
        }
        return map;
    }

    /**
     * 画面から渡された辞書で洗い替え（active 全削除 → 新規投入）。
     * <p>
     * delete → insert を同一 tx で行うため、JPA の flush 順序問題で
     * unique constraint (uq_mf_enum_active) に古い行と新しい行が同時に見える事象が起きる。
     * deleteAllInBatch + 明示 flush で DB への反映を先に確定させてから insert する。
     * また、送信された requests 内に同一 (enum_kind, english_code) の重複があれば最後勝ちで除外。
     */
    @Transactional
    public List<MMfEnumTranslation> upsertAll(List<MMfEnumTranslation> requests, Integer userNo) {
        // 1) active 行を一括物理削除 (単一 SQL で DELETE)
        List<MMfEnumTranslation> active = translationRepository.findAllByDelFlgOrderByEnumKindAscEnglishCodeAsc("0");
        if (!active.isEmpty()) {
            translationRepository.deleteAllInBatch(active);
        }
        // 2) DB に DELETE を flush して以降の insert と干渉させない
        translationRepository.flush();

        // 3) 送信された requests から (enum_kind, english_code) 重複を排除（最後勝ち）
        Map<String, MMfEnumTranslation> dedup = new LinkedHashMap<>();
        for (MMfEnumTranslation r : requests) {
            if (r.getEnumKind() == null || r.getEnglishCode() == null || r.getJapaneseName() == null) continue;
            dedup.put(r.getEnumKind() + "|" + r.getEnglishCode(), r);
        }

        Timestamp now = Timestamp.from(Instant.now());
        List<MMfEnumTranslation> toSave = new ArrayList<>();
        for (MMfEnumTranslation r : dedup.values()) {
            toSave.add(MMfEnumTranslation.builder()
                    .enumKind(r.getEnumKind())
                    .englishCode(r.getEnglishCode())
                    .japaneseName(r.getJapaneseName())
                    .delFlg("0")
                    .addDateTime(now)
                    .addUserNo(userNo)
                    .build());
        }
        return translationRepository.saveAll(toSave);
    }

    /**
     * 既存 mf_account_master と MF API /accounts を突合して翻訳を学習。
     * 既存翻訳は保持したまま、未登録の (enumKind, englishCode) だけ追加する（非破壊）。
     *
     * @return 追加件数 + 警告リスト (日本語化できなかった enum)
     */
    @Transactional
    public AutoSeedResult autoSeed(Integer userNo) {
        // 1) MF API から accounts を取得
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();
        List<MfAccount> mfAccounts = mfApiClient.listAccounts(client, accessToken).items();

        // 2) 既存 mf_account_master を (account_name) で集約
        Map<String, MfAccountMaster> localByName = new HashMap<>();
        for (MfAccountMaster m : mfAccountMasterRepository.findAll()) {
            if (m.getAccountName() != null) localByName.putIfAbsent(m.getAccountName(), m);
        }

        // 3) 既存翻訳を読み込んで重複判定用セット作成
        Map<String, MMfEnumTranslation> existingByKey = new HashMap<>();
        for (MMfEnumTranslation t : findAll()) {
            existingByKey.put(t.getEnumKind() + "|" + t.getEnglishCode(), t);
        }

        // 4) 学習: 同じ英語 enum に対して複数の日本語候補が出たら最初のもの採用（順序: MF 返却順）
        Map<String, String> learnedFS = new LinkedHashMap<>();
        Map<String, String> learnedCat = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();

        for (MfAccount acc : mfAccounts) {
            if (acc.name() == null) continue;
            MfAccountMaster local = localByName.get(acc.name());

            String fstEn = acc.financialStatementType();
            String catEn = acc.category();

            if (fstEn != null && !learnedFS.containsKey(fstEn)
                    && !existingByKey.containsKey(KIND_FINANCIAL_STATEMENT + "|" + fstEn)) {
                if (local != null && local.getReportName() != null) {
                    learnedFS.put(fstEn, local.getReportName());
                } else {
                    // 後で既存行から引けない場合は保留（別の MF account で見つかる可能性もある）
                }
            }

            if (catEn != null && !learnedCat.containsKey(catEn)
                    && !existingByKey.containsKey(KIND_CATEGORY + "|" + catEn)) {
                if (local != null && local.getCategory() != null) {
                    learnedCat.put(catEn, local.getCategory());
                }
            }
        }

        // 5) 学習できなかった enum は「行だけ追加して日本語欄を空」でインサート。
        //    UI 側でユーザーが日本語名だけ入力して保存できる UX にする。
        java.util.Set<String> seenFsEn = new java.util.HashSet<>();
        java.util.Set<String> seenCatEn = new java.util.HashSet<>();
        for (MfAccount acc : mfAccounts) {
            if (acc.financialStatementType() != null) seenFsEn.add(acc.financialStatementType());
            if (acc.category() != null) seenCatEn.add(acc.category());
        }

        Timestamp now = Timestamp.from(Instant.now());
        List<MMfEnumTranslation> toInsert = new ArrayList<>();
        for (Map.Entry<String, String> e : learnedFS.entrySet()) {
            toInsert.add(build(KIND_FINANCIAL_STATEMENT, e.getKey(), e.getValue(), now, userNo));
        }
        for (Map.Entry<String, String> e : learnedCat.entrySet()) {
            toInsert.add(build(KIND_CATEGORY, e.getKey(), e.getValue(), now, userNo));
        }
        for (String en : seenFsEn) {
            if (!learnedFS.containsKey(en) && !existingByKey.containsKey(KIND_FINANCIAL_STATEMENT + "|" + en)) {
                toInsert.add(build(KIND_FINANCIAL_STATEMENT, en, "", now, userNo));
                unresolved.add(KIND_FINANCIAL_STATEMENT + ": " + en);
            }
        }
        for (String en : seenCatEn) {
            if (!learnedCat.containsKey(en) && !existingByKey.containsKey(KIND_CATEGORY + "|" + en)) {
                toInsert.add(build(KIND_CATEGORY, en, "", now, userNo));
                unresolved.add(KIND_CATEGORY + ": " + en);
            }
        }

        // 6) DB に追加 insert（既存行は触らない）
        translationRepository.saveAll(toInsert);

        log.info("MF enum 翻訳 autoSeed: 学習追加={}件（うち要手入力={}件）",
                toInsert.size(), unresolved.size());
        return new AutoSeedResult(toInsert.size(), unresolved);
    }

    private MMfEnumTranslation build(String kind, String code, String ja, Timestamp now, Integer userNo) {
        return MMfEnumTranslation.builder()
                .enumKind(kind)
                .englishCode(code)
                .japaneseName(ja)
                .delFlg("0")
                .addDateTime(now)
                .addUserNo(userNo)
                .build();
    }

    /** 指定 enumKind / englishCode の翻訳が既にあるか確認（sync が未知 enum を検知する用）。 */
    public Optional<MMfEnumTranslation> findOne(String enumKind, String englishCode) {
        return translationRepository.findByEnumKindAndEnglishCodeAndDelFlg(enumKind, englishCode, "0");
    }

    /**
     * sync 時に新しく出現した英語 enum をとりあえず japanese_name=englishCode で追加しておく（UI で手修正してもらう）。
     */
    @Transactional
    public void registerUnknownEnum(String enumKind, String englishCode, Integer userNo) {
        if (findOne(enumKind, englishCode).isPresent()) return;
        translationRepository.save(build(enumKind, englishCode, englishCode,
                Timestamp.from(Instant.now()), userNo));
    }

    /** 追加件数 + 未解決 enum のリスト。 */
    public record AutoSeedResult(int added, List<String> unresolved) {
        public int unresolvedCount() { return unresolved != null ? unresolved.size() : 0; }
    }
}
