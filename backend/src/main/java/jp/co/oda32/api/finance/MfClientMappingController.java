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

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping
    public ResponseEntity<MfClientMappingResponse> create(@Valid @RequestBody MfClientMappingRequest req) {
        // SF-B01: write 系 (POST/PUT/DELETE) を admin 限定で統一。
        // 旧仕様の「一般ユーザ追加可」は cashbook 取込 UX 起点だったが、
        // マスタ汚染リスク回避のため admin 限定化 (運用は admin に依頼で対応)。
        return ResponseEntity.ok(MfClientMappingResponse.from(service.create(req)));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/{id}")
    public ResponseEntity<MfClientMappingResponse> update(@PathVariable Integer id, @Valid @RequestBody MfClientMappingRequest req) {
        return ResponseEntity.ok(MfClientMappingResponse.from(service.update(id, req)));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
