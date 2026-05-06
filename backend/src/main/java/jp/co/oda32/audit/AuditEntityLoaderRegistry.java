package jp.co.oda32.audit;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * T2 (C5): {@link AuditEntityLoader} 実装を table 名で索引するレジストリ。
 * <p>
 * Spring が全 {@link AuditEntityLoader} Bean を List で注入するため、新規 loader を
 * {@code @Component} で追加するだけで自動的に解決される。
 * <p>
 * 同一 table に複数 loader が登録された場合は IllegalStateException で起動時に検知する。
 *
 * @since 2026-05-04 (C5)
 */
@Component
public class AuditEntityLoaderRegistry {

    private final Map<String, AuditEntityLoader> byTable;

    public AuditEntityLoaderRegistry(List<AuditEntityLoader> loaders) {
        Map<String, AuditEntityLoader> map = new HashMap<>();
        for (AuditEntityLoader loader : loaders) {
            String table = loader.table();
            if (table == null || table.isBlank()) {
                throw new IllegalStateException(
                        "AuditEntityLoader implementation has blank table(): "
                                + loader.getClass().getName());
            }
            AuditEntityLoader prev = map.putIfAbsent(table, loader);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate AuditEntityLoader for table=" + table
                                + ": " + prev.getClass().getName()
                                + " and " + loader.getClass().getName());
            }
        }
        this.byTable = map;
    }

    public Optional<AuditEntityLoader> findByTable(String table) {
        if (table == null) return Optional.empty();
        return Optional.ofNullable(byTable.get(table));
    }
}
