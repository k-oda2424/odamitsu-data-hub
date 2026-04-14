package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.finance.PaymentMfImportService;
import jp.co.oda32.domain.service.finance.PaymentMfRuleService;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfHistoryResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleRequest;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleResponse;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/finance/payment-mf")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class PaymentMfImportController {

    private final PaymentMfImportService importService;
    private final PaymentMfRuleService ruleService;
    private final TPaymentMfImportHistoryRepository historyRepository;

    // ---- インポート ----

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file) {
        try {
            PaymentMfPreviewResponse res = importService.preview(file);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            log.warn("買掛仕入MFプレビュー: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            log.error("買掛仕入MFプレビュー失敗", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "ファイル解析中にエラーが発生しました"));
        }
    }

    @PostMapping("/preview/{uploadId}")
    public ResponseEntity<?> rePreview(@PathVariable String uploadId) {
        try {
            return ResponseEntity.ok(importService.rePreview(uploadId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/convert/{uploadId}")
    public ResponseEntity<?> convert(@PathVariable String uploadId,
                                     @AuthenticationPrincipal LoginUser user) {
        try {
            byte[] csv = importService.convert(uploadId, user == null ? null : user.getUser().getLoginUserNo());
            String fileName = "買掛仕入MFインポートファイル.csv";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"payment_mf.csv\"; filename*=UTF-8''" + encoded)
                    .body(csv);
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 買掛金一覧への一括検証反映 ----

    @PostMapping("/verify/{uploadId}")
    public ResponseEntity<?> verify(@PathVariable String uploadId,
                                    @AuthenticationPrincipal LoginUser user) {
        try {
            var result = importService.applyVerification(
                    uploadId, user == null ? null : user.getUser().getLoginUserNo());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 履歴 ----

    @GetMapping("/history")
    public ResponseEntity<List<PaymentMfHistoryResponse>> history() {
        return ResponseEntity.ok(
                historyRepository.findByShopNoAndDelFlgOrderByTransferDateDescIdDesc(1, "0")
                        .stream().map(PaymentMfHistoryResponse::from).toList());
    }

    @GetMapping("/history/{id}/csv")
    public ResponseEntity<?> historyCsv(@PathVariable Integer id) {
        try {
            byte[] csv = importService.getHistoryCsv(id);
            String fileName = "買掛仕入MFインポートファイル.csv";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"payment_mf.csv\"; filename*=UTF-8''" + encoded)
                    .body(csv);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---- ルールマスタCRUD ----

    @GetMapping("/rules")
    public ResponseEntity<List<PaymentMfRuleResponse>> rules() {
        return ResponseEntity.ok(ruleService.findAll().stream()
                .map(PaymentMfRuleResponse::from).toList());
    }

    @PostMapping("/rules")
    public ResponseEntity<PaymentMfRuleResponse> createRule(@Valid @RequestBody PaymentMfRuleRequest req,
                                                            @AuthenticationPrincipal LoginUser user) {
        // 未登録送り先の追加は一般ユーザもOK（UX優先）
        return ResponseEntity.ok(PaymentMfRuleResponse.from(
                ruleService.create(req, user == null ? null : user.getUser().getLoginUserNo())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/rules/{id}")
    public ResponseEntity<PaymentMfRuleResponse> updateRule(@PathVariable Integer id,
                                                            @Valid @RequestBody PaymentMfRuleRequest req,
                                                            @AuthenticationPrincipal LoginUser user) {
        return ResponseEntity.ok(PaymentMfRuleResponse.from(
                ruleService.update(id, req, user == null ? null : user.getUser().getLoginUserNo())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Integer id,
                                        @AuthenticationPrincipal LoginUser user) {
        ruleService.delete(id, user == null ? null : user.getUser().getLoginUserNo());
        return ResponseEntity.noContent().build();
    }
}
