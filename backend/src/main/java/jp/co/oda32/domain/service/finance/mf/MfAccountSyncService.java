package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MF /accounts + /taxes + m_mf_enum_translation を元に、
 * {@code mf_account_master} を同期（洗い替え）する Service。
 *
 * <p>既存行とのマッチングキー: {@code (account_name, sub_account_name)}
 * （sub_account_name は null/空を同一視）。
 *
 * <ul>
 *   <li>INSERT: MF にあってローカルに無い行</li>
 *   <li>UPDATE: 両方にあってフィールド値が異なる行</li>
 *   <li>DELETE: ローカルにあって MF に無い行（物理削除、ユーザー決定）</li>
 * </ul>
 *
 * @since 2026/04/21
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfAccountSyncService {

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final MfEnumTranslationService translationService;

    private static final int SAMPLE_SIZE = 10;

    /** dry-run: 差分計算のみ行い、DB 書き込みはしない。 */
    public SyncResult preview() {
        Plan plan = buildPlan();
        return toResult(plan, false);
    }

    /** 実際に DB 洗い替えを実行する。 */
    @Transactional
    public SyncResult apply(Integer userNo) {
        Plan plan = buildPlan();
        LocalDateTime now = LocalDateTime.now();

        // DELETE
        if (!plan.toDelete.isEmpty()) {
            mfAccountMasterRepository.deleteAllInBatch(plan.toDelete);
        }
        // UPDATE
        for (MfAccountMaster m : plan.toUpdate) {
            m.setUpdatedAt(now);
        }
        mfAccountMasterRepository.saveAll(plan.toUpdate);
        // INSERT
        for (MfAccountMaster m : plan.toInsert) {
            if (m.getCreatedAt() == null) m.setCreatedAt(now);
            m.setUpdatedAt(now);
        }
        mfAccountMasterRepository.saveAll(plan.toInsert);

        log.info("mf_account_master 同期完了: INSERT={}, UPDATE={}, DELETE={}, UNKNOWN_ENUM={}",
                plan.toInsert.size(), plan.toUpdate.size(), plan.toDelete.size(), plan.unknownEnums.size());
        return toResult(plan, true);
    }

    // ====== 差分計算 ======

    private Plan buildPlan() {
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();

        List<MfAccount> mfAccounts = mfApiClient.listAccounts(client, accessToken).items();
        List<MfTax> mfTaxes = mfApiClient.listTaxes(client, accessToken).items();

        // 翻訳辞書
        Map<String, String> translations = translationService.buildLookup();
        // tax_id -> tax.name
        Map<String, String> taxNameById = new HashMap<>();
        for (MfTax t : mfTaxes) {
            if (t.id() != null && t.name() != null) taxNameById.put(t.id(), t.name());
        }

        // desired 構築
        List<MfAccountMaster> desired = new ArrayList<>();
        List<String> unknownEnums = new ArrayList<>();
        int order = 0;
        for (MfAccount acc : mfAccounts) {
            if (acc.name() == null) continue;

            String reportName = translate(translations,
                    MfEnumTranslationService.KIND_FINANCIAL_STATEMENT,
                    acc.financialStatementType(), unknownEnums);
            String category = translate(translations,
                    MfEnumTranslationService.KIND_CATEGORY,
                    acc.category(), unknownEnums);

            if (acc.subAccounts() == null || acc.subAccounts().isEmpty()) {
                // 補助科目が無い勘定科目は 1 行のみ（sub_account_name=null）
                desired.add(buildRow(
                        reportName, category, acc.name(), acc.name(), null,
                        taxNameById.get(acc.taxId()),
                        nullIfBlank(acc.searchKey()),
                        Boolean.TRUE.equals(acc.available()),
                        order++));
            } else {
                for (MfAccount.MfSubAccount sub : acc.subAccounts()) {
                    desired.add(buildRow(
                            reportName, category, acc.name(), acc.name(), sub.name(),
                            taxNameById.getOrDefault(sub.taxId(), taxNameById.get(acc.taxId())),
                            nullIfBlank(sub.searchKey()),
                            Boolean.TRUE.equals(acc.available()),
                            order++));
                }
            }
        }

        // 既存ローカル
        List<MfAccountMaster> current = mfAccountMasterRepository.findAll();
        Map<String, MfAccountMaster> currentByKey = new HashMap<>();
        for (MfAccountMaster m : current) {
            currentByKey.put(keyOf(m.getAccountName(), m.getSubAccountName()), m);
        }

        // 差分
        List<MfAccountMaster> toInsert = new ArrayList<>();
        List<MfAccountMaster> toUpdate = new ArrayList<>();
        List<FieldDiff> updateSamples = new ArrayList<>();
        Map<String, MfAccountMaster> desiredByKey = new LinkedHashMap<>();

        for (MfAccountMaster d : desired) {
            String key = keyOf(d.getAccountName(), d.getSubAccountName());
            // 同一 key が desired に複数あれば最後勝ち（MF 側で稀に重複名がある場合の保険）
            desiredByKey.put(key, d);
        }

        for (Map.Entry<String, MfAccountMaster> e : desiredByKey.entrySet()) {
            MfAccountMaster d = e.getValue();
            MfAccountMaster existing = currentByKey.get(e.getKey());
            if (existing == null) {
                toInsert.add(d);
            } else {
                FieldDiff diff = diffOf(existing, d);
                if (diff != null) {
                    applyUpdate(existing, d);
                    toUpdate.add(existing);
                    if (updateSamples.size() < SAMPLE_SIZE) updateSamples.add(diff);
                }
            }
        }

        List<MfAccountMaster> toDelete = new ArrayList<>();
        for (Map.Entry<String, MfAccountMaster> e : currentByKey.entrySet()) {
            if (!desiredByKey.containsKey(e.getKey())) toDelete.add(e.getValue());
        }

        Collections.sort(unknownEnums);
        return new Plan(toInsert, toUpdate, toDelete, updateSamples, unknownEnums);
    }

    /** 翻訳辞書から日本語を引く。未解決なら warning 記録して英語のまま返す。 */
    private String translate(Map<String, String> dict, String kind, String englishCode, List<String> unknownEnums) {
        if (englishCode == null) return null;
        String key = kind + "|" + englishCode;
        String ja = dict.get(key);
        if (ja != null && !ja.isBlank()) return ja;
        String record = kind + ": " + englishCode;
        if (!unknownEnums.contains(record)) unknownEnums.add(record);
        return englishCode; // fallback
    }

    private MfAccountMaster buildRow(String reportName, String category,
                                      String financialStatementItem, String accountName,
                                      String subAccountName, String taxClassification,
                                      String searchKey, boolean isActive, int displayOrder) {
        MfAccountMaster m = new MfAccountMaster();
        m.setReportName(Objects.requireNonNullElse(reportName, ""));
        m.setCategory(Objects.requireNonNullElse(category, ""));
        m.setFinancialStatementItem(Objects.requireNonNullElse(financialStatementItem, ""));
        m.setAccountName(Objects.requireNonNullElse(accountName, ""));
        m.setSubAccountName(nullIfBlank(subAccountName));
        m.setTaxClassification(taxClassification);
        m.setSearchKey(searchKey);
        m.setIsActive(isActive);
        m.setDisplayOrder(displayOrder);
        return m;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String keyOf(String accountName, String subAccountName) {
        return (accountName == null ? "" : accountName) + "|" + (subAccountName == null ? "" : subAccountName);
    }

    /** 差分があれば FieldDiff を返す。無ければ null。 */
    private FieldDiff diffOf(MfAccountMaster current, MfAccountMaster desired) {
        boolean same = Objects.equals(current.getReportName(), desired.getReportName())
                && Objects.equals(current.getCategory(), desired.getCategory())
                && Objects.equals(current.getFinancialStatementItem(), desired.getFinancialStatementItem())
                && Objects.equals(current.getAccountName(), desired.getAccountName())
                && Objects.equals(current.getSubAccountName(), desired.getSubAccountName())
                && Objects.equals(current.getTaxClassification(), desired.getTaxClassification())
                && Objects.equals(current.getSearchKey(), desired.getSearchKey())
                && Objects.equals(current.getIsActive(), desired.getIsActive())
                && Objects.equals(current.getDisplayOrder(), desired.getDisplayOrder());
        if (same) return null;

        List<String> changes = new ArrayList<>();
        if (!Objects.equals(current.getReportName(), desired.getReportName()))
            changes.add("report_name: " + current.getReportName() + " → " + desired.getReportName());
        if (!Objects.equals(current.getCategory(), desired.getCategory()))
            changes.add("category: " + current.getCategory() + " → " + desired.getCategory());
        if (!Objects.equals(current.getTaxClassification(), desired.getTaxClassification()))
            changes.add("tax: " + current.getTaxClassification() + " → " + desired.getTaxClassification());
        if (!Objects.equals(current.getSearchKey(), desired.getSearchKey()))
            changes.add("search_key: " + current.getSearchKey() + " → " + desired.getSearchKey());
        if (!Objects.equals(current.getIsActive(), desired.getIsActive()))
            changes.add("is_active: " + current.getIsActive() + " → " + desired.getIsActive());
        if (!Objects.equals(current.getDisplayOrder(), desired.getDisplayOrder()))
            changes.add("order: " + current.getDisplayOrder() + " → " + desired.getDisplayOrder());
        return new FieldDiff(
                current.getAccountName(),
                current.getSubAccountName(),
                String.join(", ", changes));
    }

    private void applyUpdate(MfAccountMaster target, MfAccountMaster source) {
        target.setReportName(source.getReportName());
        target.setCategory(source.getCategory());
        target.setFinancialStatementItem(source.getFinancialStatementItem());
        target.setAccountName(source.getAccountName());
        target.setSubAccountName(source.getSubAccountName());
        target.setTaxClassification(source.getTaxClassification());
        target.setSearchKey(source.getSearchKey());
        target.setIsActive(source.getIsActive());
        target.setDisplayOrder(source.getDisplayOrder());
    }

    private SyncResult toResult(Plan plan, boolean applied) {
        List<SampleRow> insertSamples = plan.toInsert.stream()
                .sorted(Comparator.comparing(MfAccountMaster::getDisplayOrder))
                .limit(SAMPLE_SIZE)
                .map(m -> new SampleRow(m.getAccountName(), m.getSubAccountName(), m.getCategory(), m.getTaxClassification()))
                .toList();
        List<SampleRow> deleteSamples = plan.toDelete.stream()
                .limit(SAMPLE_SIZE)
                .map(m -> new SampleRow(m.getAccountName(), m.getSubAccountName(), m.getCategory(), m.getTaxClassification()))
                .toList();
        return new SyncResult(
                applied,
                plan.toInsert.size(),
                plan.toUpdate.size(),
                plan.toDelete.size(),
                insertSamples,
                plan.updateSamples,
                deleteSamples,
                plan.unknownEnums);
    }

    // ====== record 定義 ======

    public record SyncResult(
            boolean applied,
            int insertCount,
            int updateCount,
            int deleteCount,
            List<SampleRow> insertSamples,
            List<FieldDiff> updateSamples,
            List<SampleRow> deleteSamples,
            List<String> unknownEnums
    ) {}

    public record SampleRow(String accountName, String subAccountName, String category, String taxClassification) {}
    public record FieldDiff(String accountName, String subAccountName, String changes) {}

    private record Plan(
            List<MfAccountMaster> toInsert,
            List<MfAccountMaster> toUpdate,
            List<MfAccountMaster> toDelete,
            List<FieldDiff> updateSamples,
            List<String> unknownEnums
    ) {}
}
