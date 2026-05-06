package jp.co.oda32.audit.loaders;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.audit.AuditEntityLoader;
import jp.co.oda32.domain.model.embeddable.MSupplierOpeningBalancePK;
import jp.co.oda32.domain.repository.finance.MSupplierOpeningBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * T2 (C5): {@code m_supplier_opening_balance} の before/after snapshot loader。
 * <p>
 * PK JSON 形式 (3 キー): {@code shopNo, openingDate, supplierNo}.
 * <p>
 * {@code mf_fetch} は bulk UPSERT (1 操作で複数 supplier) のため Loader での単一 entity 復元は
 * 不能。その場合は PK に supplierNo が無く empty になる (Aspect は after = null となる)。
 *
 * @since 2026-05-04 (C5)
 */
@Component
@RequiredArgsConstructor
public class MSupplierOpeningBalanceAuditLoader implements AuditEntityLoader {

    private final MSupplierOpeningBalanceRepository repository;

    @Override
    public String table() {
        return "m_supplier_opening_balance";
    }

    @Override
    public Optional<Object> loadByPk(JsonNode pkJson) {
        if (pkJson == null) return Optional.empty();
        JsonNode shop = pkJson.get("shopNo");
        JsonNode openingDate = pkJson.get("openingDate");
        JsonNode supplier = pkJson.get("supplierNo");
        // mf_fetch は supplierNo を含まないので empty (bulk 操作)
        if (shop == null || openingDate == null || supplier == null
                || shop.isNull() || openingDate.isNull() || supplier.isNull()) {
            return Optional.empty();
        }
        try {
            MSupplierOpeningBalancePK pk = new MSupplierOpeningBalancePK(
                    shop.asInt(),
                    LocalDate.parse(openingDate.asText()),
                    supplier.asInt());
            return repository.findById(pk).map(o -> (Object) o);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
