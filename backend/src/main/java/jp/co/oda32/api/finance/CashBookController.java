package jp.co.oda32.api.finance;

import jp.co.oda32.domain.service.finance.CashBookConvertService;
import jp.co.oda32.dto.finance.cashbook.CashBookPreviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/finance/cashbook")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class CashBookController {

    private final CashBookConvertService service;

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file) {
        try {
            CashBookPreviewResponse res = service.preview(file);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            log.warn("現金出納帳プレビューエラー: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            log.error("現金出納帳プレビュー失敗", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "ファイル解析中にエラーが発生しました"));
        }
    }

    @PostMapping("/preview/{uploadId}")
    public ResponseEntity<?> rePreview(@PathVariable String uploadId) {
        try {
            return ResponseEntity.ok(service.rePreview(uploadId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/convert/{uploadId}")
    public ResponseEntity<?> convert(@PathVariable String uploadId) {
        try {
            byte[] csv = service.convert(uploadId);
            String fileName = "cashbook_" + uploadId.substring(0, 8) + ".csv";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded)
                    .body(csv);
            // MJ-B01: IllegalStateException の local catch を撤去。
            // MfTaxResolver の未知 tax_resolver code など内部設定異常は FinanceExceptionHandler#handleIllegalState
            // で 422 + 汎用メッセージ (内部詳細を露出しない) に翻訳される。
            // ユーザ起因の actionable error (「エラーが残存しています…N件」) は CashBookConvertService#convert
            // で IllegalArgumentException に格上げ済みなので 400 + 元メッセージで client に返る。
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
