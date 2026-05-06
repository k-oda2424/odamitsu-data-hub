package jp.co.oda32.audit.loaders;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.audit.AuditEntityLoader;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * T2 (C5): {@code t_accounts_payable_summary} の before/after snapshot loader。
 * <p>
 * PK JSON 形式 (4 キー): {@code shopNo, supplierNo, transactionMonth, taxRate}.
 * これと異なる shape の PK (例: payment_mf_apply の {uploadId, userNo}) では empty を返し、
 * Aspect 側のフォールバック (returnAsAfter) に委ねる。
 *
 * @since 2026-05-04 (C5)
 */
@Component
@RequiredArgsConstructor
public class TAccountsPayableSummaryAuditLoader implements AuditEntityLoader {

    private final TAccountsPayableSummaryRepository repository;

    @Override
    public String table() {
        return "t_accounts_payable_summary";
    }

    @Override
    public Optional<Object> loadByPk(JsonNode pkJson) {
        if (pkJson == null) return Optional.empty();
        JsonNode shop = pkJson.get("shopNo");
        JsonNode supplier = pkJson.get("supplierNo");
        JsonNode month = pkJson.get("transactionMonth");
        JsonNode rate = pkJson.get("taxRate");
        if (shop == null || supplier == null || month == null || rate == null
                || shop.isNull() || supplier.isNull() || month.isNull() || rate.isNull()) {
            return Optional.empty();
        }
        try {
            int shopNo = shop.asInt();
            int supplierNo = supplier.asInt();
            LocalDate transactionMonth = LocalDate.parse(month.asText());
            BigDecimal taxRate = new BigDecimal(rate.asText());
            var entity = repository.getByShopNoAndSupplierNoAndTransactionMonthAndTaxRate(
                    shopNo, supplierNo, transactionMonth, taxRate);
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
