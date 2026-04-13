package jp.co.oda32.domain.service.finance;

import jp.co.oda32.annotation.SkipShopCheck;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 20日締め買掛金テーブルのサービスクラス
 *
 * @author k_oda
 * @since 2024/09/10
 */
@Service
public class TAccountsPayableSummaryService {

    private final TAccountsPayableSummaryRepository repository;

    @Autowired
    public TAccountsPayableSummaryService(TAccountsPayableSummaryRepository repository) {
        this.repository = repository;
    }

    public List<TAccountsPayableSummary> findAll() {
        return repository.findAll();
    }

    @SkipShopCheck
    public Page<TAccountsPayableSummary> findPaged(Integer shopNo, Integer supplierNo, Pageable pageable) {
        Specification<TAccountsPayableSummary> spec = Specification.where(null);
        if (shopNo != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("shopNo"), shopNo));
        }
        if (supplierNo != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("supplierNo"), supplierNo));
        }
        return repository.findAll(spec, pageable);
    }

    public TAccountsPayableSummary getByPK(int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate) {
        return repository.getByShopNoAndSupplierNoAndTransactionMonthAndTaxRate(shopNo, supplierNo, transactionMonth, taxRate);
    }

    public TAccountsPayableSummary save(TAccountsPayableSummary summary) {
        // 金額の小数点以下を切り捨て
        if (summary.getTaxIncludedAmount() != null) {
            summary.setTaxIncludedAmount(summary.getTaxIncludedAmount().setScale(0, java.math.RoundingMode.DOWN));
        }
        if (summary.getTaxExcludedAmount() != null) {
            summary.setTaxExcludedAmount(summary.getTaxExcludedAmount().setScale(0, java.math.RoundingMode.DOWN));
        }
        if (summary.getTaxIncludedAmountChange() != null) {
            summary.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange().setScale(0, java.math.RoundingMode.DOWN));
        }
        if (summary.getTaxExcludedAmountChange() != null) {
            summary.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange().setScale(0, java.math.RoundingMode.DOWN));
        }
        if (summary.getPaymentDifference() != null) {
            summary.setPaymentDifference(summary.getPaymentDifference().setScale(0, java.math.RoundingMode.DOWN));
        }
        return repository.save(summary);
    }

    public List<TAccountsPayableSummary> saveAll(List<TAccountsPayableSummary> summaries) {
        // 各サマリーの金額の小数点以下を切り捨て
        for (TAccountsPayableSummary summary : summaries) {
            if (summary.getTaxIncludedAmount() != null) {
                summary.setTaxIncludedAmount(summary.getTaxIncludedAmount().setScale(0, java.math.RoundingMode.DOWN));
            }
            if (summary.getTaxExcludedAmount() != null) {
                summary.setTaxExcludedAmount(summary.getTaxExcludedAmount().setScale(0, java.math.RoundingMode.DOWN));
            }
            if (summary.getTaxIncludedAmountChange() != null) {
                summary.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange().setScale(0, java.math.RoundingMode.DOWN));
            }
            if (summary.getTaxExcludedAmountChange() != null) {
                summary.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange().setScale(0, java.math.RoundingMode.DOWN));
            }
            if (summary.getPaymentDifference() != null) {
                summary.setPaymentDifference(summary.getPaymentDifference().setScale(0, java.math.RoundingMode.DOWN));
            }
        }
        return repository.saveAll(summaries);
    }

    /**
     * 指定された取引月に対応するTAccountsPayableSummaryのリストを取得します。
     *
     * @param transactionMonth 取引月
     * @return 取引月に対応するTAccountsPayableSummaryのリスト
     */
    public List<TAccountsPayableSummary> findByTransactionMonth(LocalDate transactionMonth) {
        return repository.findByTransactionMonth(transactionMonth);
    }
}
