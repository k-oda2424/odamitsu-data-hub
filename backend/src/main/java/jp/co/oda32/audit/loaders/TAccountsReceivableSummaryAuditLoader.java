package jp.co.oda32.audit.loaders;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.audit.AuditEntityLoader;
import jp.co.oda32.domain.repository.finance.TAccountsReceivableSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * T2 (C5): {@code t_accounts_receivable_summary} の before/after snapshot loader。
 * <p>
 * PK JSON 形式 (5 キー): {@code shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag}.
 *
 * @since 2026-05-04 (C5)
 */
@Component
@RequiredArgsConstructor
public class TAccountsReceivableSummaryAuditLoader implements AuditEntityLoader {

    private final TAccountsReceivableSummaryRepository repository;

    @Override
    public String table() {
        return "t_accounts_receivable_summary";
    }

    @Override
    public Optional<Object> loadByPk(JsonNode pkJson) {
        if (pkJson == null) return Optional.empty();
        JsonNode shop = pkJson.get("shopNo");
        JsonNode partner = pkJson.get("partnerNo");
        JsonNode month = pkJson.get("transactionMonth");
        JsonNode rate = pkJson.get("taxRate");
        JsonNode otake = pkJson.get("isOtakeGarbageBag");
        if (shop == null || partner == null || month == null || rate == null || otake == null
                || shop.isNull() || partner.isNull() || month.isNull() || rate.isNull() || otake.isNull()) {
            return Optional.empty();
        }
        try {
            int shopNo = shop.asInt();
            int partnerNo = partner.asInt();
            LocalDate transactionMonth = LocalDate.parse(month.asText());
            BigDecimal taxRate = new BigDecimal(rate.asText());
            boolean isOtakeGarbageBag = otake.asBoolean();
            var entity = repository.getByShopNoAndPartnerNoAndTransactionMonthAndTaxRateAndIsOtakeGarbageBag(
                    shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
