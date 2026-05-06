package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.service.finance.MMfJournalRuleService;
import jp.co.oda32.dto.finance.cashbook.MfJournalRuleRequest;
import jp.co.oda32.dto.finance.cashbook.MfJournalRuleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/finance/mf-journal-rules")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MfJournalRuleController {

    private final MMfJournalRuleService service;

    @GetMapping
    public ResponseEntity<List<MfJournalRuleResponse>> list() {
        return ResponseEntity.ok(service.findAll().stream().map(MfJournalRuleResponse::from).toList());
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping
    public ResponseEntity<MfJournalRuleResponse> create(@Valid @RequestBody MfJournalRuleRequest req) {
        return ResponseEntity.ok(MfJournalRuleResponse.from(service.create(req)));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/{id}")
    public ResponseEntity<MfJournalRuleResponse> update(@PathVariable Integer id, @Valid @RequestBody MfJournalRuleRequest req) {
        return ResponseEntity.ok(MfJournalRuleResponse.from(service.update(id, req)));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
