package jp.co.oda32.audit.loaders;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.audit.AuditEntityLoader;
import jp.co.oda32.domain.model.embeddable.TConsistencyReviewPK;
import jp.co.oda32.domain.repository.finance.TConsistencyReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * T2 (C5): {@code t_consistency_review} の before/after snapshot loader。
 * <p>
 * PK JSON 形式 (4 キー): {@code shopNo, entryType, entryKey, transactionMonth}.
 *
 * @since 2026-05-04 (C5)
 */
@Component
@RequiredArgsConstructor
public class TConsistencyReviewAuditLoader implements AuditEntityLoader {

    private final TConsistencyReviewRepository repository;

    @Override
    public String table() {
        return "t_consistency_review";
    }

    @Override
    public Optional<Object> loadByPk(JsonNode pkJson) {
        if (pkJson == null) return Optional.empty();
        JsonNode shop = pkJson.get("shopNo");
        JsonNode entryType = pkJson.get("entryType");
        JsonNode entryKey = pkJson.get("entryKey");
        JsonNode month = pkJson.get("transactionMonth");
        if (shop == null || entryType == null || entryKey == null || month == null
                || shop.isNull() || entryType.isNull() || entryKey.isNull() || month.isNull()) {
            return Optional.empty();
        }
        try {
            TConsistencyReviewPK pk = new TConsistencyReviewPK(
                    shop.asInt(),
                    entryType.asText(),
                    entryKey.asText(),
                    LocalDate.parse(month.asText()));
            return repository.findById(pk).map(o -> (Object) o);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
