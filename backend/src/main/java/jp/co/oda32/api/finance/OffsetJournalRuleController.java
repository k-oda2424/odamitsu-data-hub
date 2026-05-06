package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.service.finance.MOffsetJournalRuleService;
import jp.co.oda32.dto.finance.OffsetJournalRuleRequest;
import jp.co.oda32.dto.finance.OffsetJournalRuleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * G2-M8: OFFSET 仕訳マスタの CRUD API。
 *
 * <p>shop_no=1 の 1 行運用が基本だが、将来 shop 追加に備えて create / delete も用意。
 * 編集は admin (shop_no=0) ユーザーのみ。
 */
@RestController
@RequestMapping("/api/v1/finance/offset-journal-rules")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class OffsetJournalRuleController {

    private final MOffsetJournalRuleService service;

    @GetMapping
    public ResponseEntity<List<OffsetJournalRuleResponse>> list() {
        return ResponseEntity.ok(
                service.findAll().stream().map(OffsetJournalRuleResponse::from).toList());
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping
    public ResponseEntity<OffsetJournalRuleResponse> create(
            @Valid @RequestBody OffsetJournalRuleRequest req) {
        return ResponseEntity.ok(OffsetJournalRuleResponse.from(service.create(req)));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/{id}")
    public ResponseEntity<OffsetJournalRuleResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody OffsetJournalRuleRequest req) {
        return ResponseEntity.ok(OffsetJournalRuleResponse.from(service.update(id, req)));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
