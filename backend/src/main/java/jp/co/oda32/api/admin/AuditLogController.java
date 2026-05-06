package jp.co.oda32.api.admin;

import jp.co.oda32.domain.service.audit.AuditLogQueryService;
import jp.co.oda32.dto.audit.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * T2: 監査ログ閲覧 (admin only)。
 * <p>
 * - {@code GET /api/v1/admin/audit-log/search} : 検索 (ページング)
 * - {@code GET /api/v1/admin/audit-log/{id}}   : 詳細 (before/after JSONB 含む)
 * - {@code GET /api/v1/admin/audit-log/operations} / {@code .../tables} : フィルタ用 distinct
 *
 * @since 2026-05-04 (T2)
 */
@RestController
@RequestMapping("/api/v1/admin/audit-log")
@PreAuthorize("@loginUserSecurityBean.isAdmin()")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService service;

    @GetMapping("/search")
    public ResponseEntity<Page<AuditLogResponse>> search(
            @RequestParam(required = false) Integer actorUserNo,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String targetTable,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                service.search(actorUserNo, operation, targetTable, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> detail(@PathVariable Long id) {
        return service.findDetail(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/operations")
    public ResponseEntity<List<String>> distinctOperations() {
        return ResponseEntity.ok(service.distinctOperations());
    }

    @GetMapping("/tables")
    public ResponseEntity<List<String>> distinctTables() {
        return ResponseEntity.ok(service.distinctTargetTables());
    }
}
