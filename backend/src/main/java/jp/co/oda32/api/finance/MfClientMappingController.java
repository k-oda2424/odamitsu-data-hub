package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.service.finance.MMfClientMappingService;
import jp.co.oda32.dto.finance.cashbook.MfClientMappingRequest;
import jp.co.oda32.dto.finance.cashbook.MfClientMappingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/finance/mf-client-mappings")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MfClientMappingController {

    private final MMfClientMappingService service;

    @GetMapping
    public ResponseEntity<List<MfClientMappingResponse>> list() {
        return ResponseEntity.ok(service.findAll().stream().map(MfClientMappingResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<MfClientMappingResponse> create(@Valid @RequestBody MfClientMappingRequest req) {
        // 一般ユーザでも追加可（現金出納帳取込のマッピング補正UX）
        return ResponseEntity.ok(MfClientMappingResponse.from(service.create(req)));
    }

    @PreAuthorize("authentication.principal.shopNo == 0")
    @PutMapping("/{id}")
    public ResponseEntity<MfClientMappingResponse> update(@PathVariable Integer id, @Valid @RequestBody MfClientMappingRequest req) {
        return ResponseEntity.ok(MfClientMappingResponse.from(service.update(id, req)));
    }

    @PreAuthorize("authentication.principal.shopNo == 0")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
