package jp.co.oda32.audit.loaders;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.audit.AuditEntityLoader;
import jp.co.oda32.domain.repository.finance.TInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * T2 (C5): {@code t_invoice} の before/after snapshot loader。
 * <p>
 * import 操作は (file, shopNo) で起動し複数 invoice 行を一括 UPSERT するため、
 * 単一 PK での before/after は不能。常に empty を返し、Aspect 側のフォールバックで
 * {@code InvoiceImportResult} を after に記録する設計。
 *
 * @since 2026-05-04 (C5)
 */
@Component
@RequiredArgsConstructor
public class TInvoiceAuditLoader implements AuditEntityLoader {

    @SuppressWarnings("unused")
    private final TInvoiceRepository repository; // 将来 invoice_id 単位の操作が増えた場合に利用

    @Override
    public String table() {
        return "t_invoice";
    }

    @Override
    public Optional<Object> loadByPk(JsonNode pkJson) {
        // import は bulk 操作のため Loader 経由の単一 entity snapshot は提供しない。
        // captureReturnAsAfter=true により Aspect 側で InvoiceImportResult が after に記録される。
        return Optional.empty();
    }
}
