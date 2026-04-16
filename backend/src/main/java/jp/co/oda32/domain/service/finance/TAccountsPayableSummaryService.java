package jp.co.oda32.domain.service.finance;

import jakarta.persistence.criteria.Predicate;
import jp.co.oda32.annotation.SkipShopCheck;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.dto.finance.AccountsPayableSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 20日締め買掛金テーブルのサービスクラス
 *
 * @author k_oda
 * @since 2024/09/10
 */
@Service
public class TAccountsPayableSummaryService {

    private static final BigDecimal MATCH_THRESHOLD = new BigDecimal("100");

    private final TAccountsPayableSummaryRepository repository;

    @Autowired
    public TAccountsPayableSummaryService(TAccountsPayableSummaryRepository repository) {
        this.repository = repository;
    }

    public List<TAccountsPayableSummary> findAll() {
        return repository.findAll();
    }

    @SkipShopCheck
    public Page<TAccountsPayableSummary> findPaged(
            Integer shopNo,
            Integer supplierNo,
            LocalDate transactionMonth,
            String verificationFilter,
            Pageable pageable) {
        Specification<TAccountsPayableSummary> spec = buildSpec(shopNo, supplierNo, transactionMonth, verificationFilter);
        return repository.findAll(spec, pageable);
    }

    @SkipShopCheck
    public AccountsPayableSummaryResponse summary(Integer shopNo, LocalDate transactionMonth) {
        Specification<TAccountsPayableSummary> spec = buildSpec(shopNo, null, transactionMonth, "all");
        List<TAccountsPayableSummary> list = repository.findAll(spec);
        long total = list.size();
        long unverified = list.stream().filter(s -> s.getVerificationResult() == null).count();
        long unmatched = list.stream().filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0).count();
        long matched = list.stream().filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 1).count();
        BigDecimal diffSum = list.stream()
                .filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0)
                .map(s -> s.getPaymentDifference() != null ? s.getPaymentDifference() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return AccountsPayableSummaryResponse.builder()
                .transactionMonth(transactionMonth)
                .totalCount(total)
                .unverifiedCount(unverified)
                .unmatchedCount(unmatched)
                .matchedCount(matched)
                .unmatchedDifferenceSum(diffSum)
                .build();
    }

    private Specification<TAccountsPayableSummary> buildSpec(
            Integer shopNo, Integer supplierNo, LocalDate transactionMonth, String verificationFilter) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (shopNo != null) {
                preds.add(cb.equal(root.get("shopNo"), shopNo));
            }
            if (supplierNo != null) {
                preds.add(cb.equal(root.get("supplierNo"), supplierNo));
            }
            if (transactionMonth != null) {
                preds.add(cb.equal(root.get("transactionMonth"), transactionMonth));
            }
            if (verificationFilter != null) {
                switch (verificationFilter) {
                    case "unverified":
                        preds.add(cb.isNull(root.get("verificationResult")));
                        break;
                    case "unmatched":
                        preds.add(cb.equal(root.get("verificationResult"), 0));
                        break;
                    case "matched":
                        preds.add(cb.equal(root.get("verificationResult"), 1));
                        break;
                    default:
                        // all: no filter
                }
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    public TAccountsPayableSummary getByPK(int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate) {
        // PostgreSQL の numeric 比較は scale 非依存だが、念のため保存フォーマット(scale=2)で問い合わせる
        BigDecimal rate = taxRate != null ? taxRate.setScale(2, RoundingMode.HALF_UP) : null;
        return repository.getByShopNoAndSupplierNoAndTransactionMonthAndTaxRate(shopNo, supplierNo, transactionMonth, rate);
    }

    /**
     * 手動で支払額を検証し、差額計算と一致判定を行います。
     * verifiedManually=true をセットし、次回 SMILE 再検証バッチで上書きされないようにします。
     */
    @Transactional
    public TAccountsPayableSummary verify(
            int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate,
            BigDecimal verifiedAmount, String note) {
        TAccountsPayableSummary summary = getByPK(shopNo, supplierNo, transactionMonth, taxRate);
        if (summary == null) {
            throw new IllegalArgumentException("対象の買掛金集計が見つかりません");
        }
        applyVerification(summary, verifiedAmount);
        summary.setVerifiedManually(Boolean.TRUE);
        summary.setVerificationNote(note);
        // 振込明細一括検証と同じ挙動: 一致なら MF出力=ON、不一致なら MF出力=OFF
        // ユーザーが後で Switch で明示的に上書き可能
        summary.setMfExportEnabled(Integer.valueOf(1).equals(summary.getVerificationResult()));
        return save(summary);
    }

    /**
     * 手動確定を解除します。次回 SMILE 再検証バッチで上書きされるようになります。
     */
    @Transactional
    public TAccountsPayableSummary releaseManualLock(
            int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate) {
        TAccountsPayableSummary summary = getByPK(shopNo, supplierNo, transactionMonth, taxRate);
        if (summary == null) {
            throw new IllegalArgumentException("対象の買掛金集計が見つかりません");
        }
        summary.setVerifiedManually(Boolean.FALSE);
        return repository.save(summary);
    }

    @Transactional
    public TAccountsPayableSummary updateMfExport(
            int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate, boolean enabled) {
        TAccountsPayableSummary summary = getByPK(shopNo, supplierNo, transactionMonth, taxRate);
        if (summary == null) {
            throw new IllegalArgumentException("対象の買掛金集計が見つかりません");
        }
        summary.setMfExportEnabled(enabled);
        return repository.save(summary);
    }

    private void applyVerification(TAccountsPayableSummary summary, BigDecimal verifiedAmount) {
        // 請求額（振込明細 or 手入力）を専用列に保存。tax_included_amount は
        // MF出力スナップショットとして別管理するため、検証時はここに保存する。
        summary.setVerifiedAmount(verifiedAmount);
        summary.setTaxIncludedAmount(verifiedAmount);

        // 税率は集計時に必ず入っている前提。null の場合はデータ不整合として fail fast し、
        // 誤った税抜額で上書きすることを避ける（非課税0%と10%が取り違えられる等）。
        if (summary.getTaxRate() == null) {
            throw new IllegalStateException(
                    "買掛金集計の taxRate が null です。集計バッチを再実行してください: shopNo="
                            + summary.getShopNo() + ", supplierNo=" + summary.getSupplierNo()
                            + ", transactionMonth=" + summary.getTransactionMonth());
        }
        BigDecimal rate = summary.getTaxRate();
        BigDecimal divisor = BigDecimal.ONE.add(rate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        BigDecimal taxExcluded = verifiedAmount.divide(divisor, 0, RoundingMode.DOWN);
        summary.setTaxExcludedAmount(taxExcluded);

        BigDecimal baseTaxIncluded = summary.getTaxIncludedAmountChange() != null
                ? summary.getTaxIncludedAmountChange()
                : BigDecimal.ZERO;
        BigDecimal difference = verifiedAmount.subtract(baseTaxIncluded);
        summary.setPaymentDifference(difference);

        boolean matched = difference.abs().compareTo(MATCH_THRESHOLD) <= 0;
        summary.setVerificationResult(matched ? 1 : 0);
    }

    public TAccountsPayableSummary save(TAccountsPayableSummary summary) {
        // 金額の小数点以下を切り捨て
        if (summary.getTaxIncludedAmount() != null) {
            summary.setTaxIncludedAmount(summary.getTaxIncludedAmount().setScale(0, RoundingMode.DOWN));
        }
        if (summary.getTaxExcludedAmount() != null) {
            summary.setTaxExcludedAmount(summary.getTaxExcludedAmount().setScale(0, RoundingMode.DOWN));
        }
        if (summary.getTaxIncludedAmountChange() != null) {
            summary.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange().setScale(0, RoundingMode.DOWN));
        }
        if (summary.getTaxExcludedAmountChange() != null) {
            summary.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange().setScale(0, RoundingMode.DOWN));
        }
        if (summary.getPaymentDifference() != null) {
            summary.setPaymentDifference(summary.getPaymentDifference().setScale(0, RoundingMode.DOWN));
        }
        return repository.save(summary);
    }

    public List<TAccountsPayableSummary> saveAll(List<TAccountsPayableSummary> summaries) {
        for (TAccountsPayableSummary summary : summaries) {
            if (summary.getTaxIncludedAmount() != null) {
                summary.setTaxIncludedAmount(summary.getTaxIncludedAmount().setScale(0, RoundingMode.DOWN));
            }
            if (summary.getTaxExcludedAmount() != null) {
                summary.setTaxExcludedAmount(summary.getTaxExcludedAmount().setScale(0, RoundingMode.DOWN));
            }
            if (summary.getTaxIncludedAmountChange() != null) {
                summary.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange().setScale(0, RoundingMode.DOWN));
            }
            if (summary.getTaxExcludedAmountChange() != null) {
                summary.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange().setScale(0, RoundingMode.DOWN));
            }
            if (summary.getPaymentDifference() != null) {
                summary.setPaymentDifference(summary.getPaymentDifference().setScale(0, RoundingMode.DOWN));
            }
        }
        return repository.saveAll(summaries);
    }

    public List<TAccountsPayableSummary> findByTransactionMonth(LocalDate transactionMonth) {
        return repository.findByTransactionMonth(transactionMonth);
    }
}
