package jp.co.oda32.audit.loaders;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.audit.AuditEntityLoader;
import jp.co.oda32.domain.repository.finance.MPartnerGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * T2 (C5): {@code m_partner_group} の before/after snapshot loader。
 * <p>
 * PK JSON 形式:
 * <ul>
 *   <li>UPDATE / DELETE: {@code {partnerGroupId: <int>}}</li>
 *   <li>INSERT: {@code {groupName, shopNo}} (id 未確定のため empty を返す → after は null)</li>
 * </ul>
 *
 * @since 2026-05-04 (C5)
 */
@Component
@RequiredArgsConstructor
public class MPartnerGroupAuditLoader implements AuditEntityLoader {

    private final MPartnerGroupRepository repository;

    @Override
    public String table() {
        return "m_partner_group";
    }

    @Override
    public Optional<Object> loadByPk(JsonNode pkJson) {
        if (pkJson == null) return Optional.empty();
        JsonNode id = pkJson.get("partnerGroupId");
        if (id == null || id.isNull() || !id.canConvertToInt()) {
            return Optional.empty();
        }
        try {
            return repository.findById(id.asInt()).map(o -> (Object) o);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
