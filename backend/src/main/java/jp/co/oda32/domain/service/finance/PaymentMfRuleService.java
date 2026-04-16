package jp.co.oda32.domain.service.finance;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentMfRuleService {

    private final MPaymentMfRuleRepository repository;
    private final MPaymentSupplierService paymentSupplierService;

    public List<MPaymentMfRule> findAll() {
        return repository.findByDelFlgOrderByPriorityAscIdAsc("0");
    }

    @Transactional
    public MPaymentMfRule create(PaymentMfRuleRequest req, Integer userNo) {
        MPaymentMfRule rule = MPaymentMfRule.builder()
                .sourceName(req.getSourceName())
                .paymentSupplierCode(blankToNull(req.getPaymentSupplierCode()))
                .ruleKind(req.getRuleKind())
                .debitAccount(req.getDebitAccount())
                .debitSubAccount(blankToNull(req.getDebitSubAccount()))
                .debitDepartment(blankToNull(req.getDebitDepartment()))
                .debitTaxCategory(req.getDebitTaxCategory())
                .creditAccount(defaultStr(req.getCreditAccount(), "資金複合"))
                .creditSubAccount(blankToNull(req.getCreditSubAccount()))
                .creditDepartment(blankToNull(req.getCreditDepartment()))
                .creditTaxCategory(defaultStr(req.getCreditTaxCategory(), "対象外"))
                .summaryTemplate(req.getSummaryTemplate())
                .tag(blankToNull(req.getTag()))
                .priority(req.getPriority() == null ? 100 : req.getPriority())
                .delFlg("0")
                .addDateTime(LocalDateTime.now())
                .addUserNo(userNo)
                .build();
        return repository.save(rule);
    }

    @Transactional
    public MPaymentMfRule update(Integer id, PaymentMfRuleRequest req, Integer userNo) {
        MPaymentMfRule r = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルールが見つかりません: " + id));
        r.setSourceName(req.getSourceName());
        r.setPaymentSupplierCode(blankToNull(req.getPaymentSupplierCode()));
        r.setRuleKind(req.getRuleKind());
        r.setDebitAccount(req.getDebitAccount());
        r.setDebitSubAccount(blankToNull(req.getDebitSubAccount()));
        r.setDebitDepartment(blankToNull(req.getDebitDepartment()));
        r.setDebitTaxCategory(req.getDebitTaxCategory());
        r.setCreditAccount(defaultStr(req.getCreditAccount(), "資金複合"));
        r.setCreditSubAccount(blankToNull(req.getCreditSubAccount()));
        r.setCreditDepartment(blankToNull(req.getCreditDepartment()));
        r.setCreditTaxCategory(defaultStr(req.getCreditTaxCategory(), "対象外"));
        r.setSummaryTemplate(req.getSummaryTemplate());
        r.setTag(blankToNull(req.getTag()));
        if (req.getPriority() != null) r.setPriority(req.getPriority());
        r.setModifyDateTime(LocalDateTime.now());
        r.setModifyUserNo(userNo);
        return repository.save(r);
    }

    @Transactional
    public void delete(Integer id, Integer userNo) {
        MPaymentMfRule r = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルールが見つかりません: " + id));
        r.setDelFlg("1");
        r.setModifyDateTime(LocalDateTime.now());
        r.setModifyUserNo(userNo);
        repository.save(r);
    }

    /**
     * PAYABLE ルールのうち payment_supplier_code 未設定のものを、
     * m_payment_supplier の payment_supplier_name と名寄せして一括補完する。
     *
     * @param dryRun true の場合はDBを更新せず、マッチ結果のみ返す
     */
    @Transactional
    public BackfillResult backfillPaymentSupplierCodes(boolean dryRun, Integer userNo) {
        List<MPaymentMfRule> rules = repository.findByDelFlgOrderByPriorityAscIdAsc("0");
        List<MPaymentSupplier> suppliers = paymentSupplierService.findByShopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO);

        // 正規化名 → 候補支払先リスト（複数ヒット検知のため List）
        Map<String, List<MPaymentSupplier>> byNormName = new LinkedHashMap<>();
        for (MPaymentSupplier s : suppliers) {
            if (s.getPaymentSupplierName() == null) continue;
            String key = normalizeCompanyName(s.getPaymentSupplierName());
            if (key.isEmpty()) continue;
            byNormName.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<BackfillItem> items = new ArrayList<>();
        int matched = 0, ambiguous = 0, notFound = 0, skipped = 0;

        for (MPaymentMfRule rule : rules) {
            if (!"PAYABLE".equals(rule.getRuleKind())) { skipped++; continue; }
            if (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()) {
                skipped++;
                continue;
            }
            String normSource = normalizeCompanyName(rule.getSourceName());

            // 完全一致 → 部分一致(include)の順に探索。
            // 部分一致は「マスタ名が振込明細(rule.sourceName)の部分文字列」の一方向のみ。
            // 両方向 contains にすると "カミイソ" と "カミイソ産商" が相互ヒットする過剰マッチが発生する。
            // 部分一致の最小桁数を 4 文字に引き上げて誤マッチをさらに抑制する
            // （3 文字だと "abc" を含むだけで無関係な会社にマッチするリスクがあるため）。
            List<MPaymentSupplier> cands = byNormName.getOrDefault(normSource, List.of());
            if (cands.isEmpty() && normSource.length() >= 4) {
                cands = new ArrayList<>();
                for (var e : byNormName.entrySet()) {
                    if (e.getKey().length() >= 4 && normSource.contains(e.getKey())) {
                        cands.addAll(e.getValue());
                    }
                }
            }
            // 正規化後4文字未満は誤マッチ温床のため一律マッチ対象外
            if (normSource.length() < 4) cands = List.of();

            BackfillItem.BackfillItemBuilder b = BackfillItem.builder()
                    .ruleId(rule.getId())
                    .sourceName(rule.getSourceName());

            if (cands.isEmpty()) {
                items.add(b.status("NOT_FOUND").build());
                notFound++;
            } else if (cands.size() == 1 || cands.stream().map(MPaymentSupplier::getPaymentSupplierCode).distinct().count() == 1) {
                // 候補が1件、または複数でも同一支払先コード（登録重複）なら採用
                MPaymentSupplier pick = cands.get(0);
                items.add(b.status("MATCHED")
                        .candidateCode(pick.getPaymentSupplierCode())
                        .candidateName(pick.getPaymentSupplierName())
                        .build());
                matched++;
                if (!dryRun) {
                    rule.setPaymentSupplierCode(pick.getPaymentSupplierCode());
                    rule.setModifyDateTime(LocalDateTime.now());
                    rule.setModifyUserNo(userNo);
                    repository.save(rule);
                }
            } else {
                cands.sort(Comparator.comparing(MPaymentSupplier::getPaymentSupplierCode));
                List<String> alts = new ArrayList<>();
                for (MPaymentSupplier s : cands) {
                    alts.add(s.getPaymentSupplierCode() + " " + s.getPaymentSupplierName());
                }
                items.add(b.status("AMBIGUOUS").alternatives(alts).build());
                ambiguous++;
            }
        }

        return BackfillResult.builder()
                .dryRun(dryRun)
                .matchedCount(matched)
                .ambiguousCount(ambiguous)
                .notFoundCount(notFound)
                .skippedCount(skipped)
                .items(items)
                .build();
    }

    /**
     * 会社名表記ゆれ（全半角・株式会社/㈱・支店記号[20]など）を吸収した正規化名を返す。
     * マスタ名寄せ専用。UI上の検索正規化よりも厳しめ。
     */
    static String normalizeCompanyName(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        // [20], [5], [20竹], [20手] など店舗・支払サイト注記を除去
        s = s.replaceAll("\\[[^\\]]*\\]", "");
        // 全角括弧 → 半角扱いは NFKC でされる。(株)/(有) 表記を除去
        s = s.replace("(株)", "").replace("(有)", "")
             .replace("㈱", "").replace("㈲", "");
        // 会社種別語を除去
        s = s.replaceAll("株式会社|有限会社|合同会社|合資会社|合名会社", "");
        // 支店種別を除去（松/竹/梅/手 — 末尾一文字のみ）
        // 単語中に現れると過剰マッチになるため、末尾一致のみ削る
        s = s.replaceAll("[松竹梅手]\\s*$", "");
        // 空白と記号を除去
        s = s.replaceAll("[\\s\\u3000,.\\-・。、]", "");
        return s.trim().toLowerCase();
    }

    @Data
    @Builder
    public static class BackfillResult {
        private boolean dryRun;
        private int matchedCount;
        private int ambiguousCount;
        private int notFoundCount;
        private int skippedCount;
        private List<BackfillItem> items;
    }

    @Data
    @Builder
    public static class BackfillItem {
        private Integer ruleId;
        private String sourceName;
        /** MATCHED / AMBIGUOUS / NOT_FOUND */
        private String status;
        private String candidateCode;
        private String candidateName;
        private List<String> alternatives;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private static String defaultStr(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }
}
