package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.finance.PaymentMfImportService;
import jp.co.oda32.domain.service.finance.PaymentMfRuleService;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfApplyRequest;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfAuxRowResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfHistoryResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleRequest;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleResponse;
import jp.co.oda32.dto.finance.paymentmf.VerifiedExportPreviewResponse;
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

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/convert/{uploadId}")
    public ResponseEntity<?> convert(@PathVariable String uploadId,
                                     @AuthenticationPrincipal LoginUser user) {
        try {
            // SF-C20: ファイル名は payment_mf_${yyyymmdd}.csv / 買掛仕入MFインポートファイル_${yyyymmdd}.csv
            // (cached.transferDate ベース) で /export-verified と統一する。
            java.time.LocalDate transferDate = importService.getCachedTransferDate(uploadId);
            byte[] csv = importService.convert(uploadId, user == null ? null : user.getUser().getLoginUserNo());
            String yyyymmdd = transferDate == null ? "unknown"
                    : transferDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fileName = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
            String asciiName = "payment_mf_" + yyyymmdd + ".csv";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded)
                    .body(csv);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        // IllegalStateException は FinanceExceptionHandler#handleIllegalState で 422 + 汎用メッセージ統一
    }

    // ---- 買掛金一覧への一括検証反映 ----

    /**
     * 振込明細 Excel の検証結果を t_accounts_payable_summary に一括反映する。
     *
     * <p>G2-M2 (2026-05-06): リクエストボディで {@code force} フラグを受ける。
     * <ul>
     *   <li>ボディ省略 / {@code force=false}: per-supplier 1 円不一致が 1 件でもあれば
     *       422 + {@code PER_SUPPLIER_MISMATCH} で拒否 ({@link FinanceExceptionHandler})。
     *       client は preview で違反を確認し、Excel 修正 → 再アップロードする運用。</li>
     *   <li>{@code force=true}: 違反を許容して反映。{@code finance_audit_log} に
     *       {@code FORCE_APPLIED: per-supplier mismatches=...} の補足 row が記録される。</li>
     * </ul>
     */
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/verify/{uploadId}")
    public ResponseEntity<?> verify(@PathVariable String uploadId,
                                    @RequestBody(required = false) PaymentMfApplyRequest request,
                                    @AuthenticationPrincipal LoginUser user) {
        try {
            boolean force = request != null && request.isForce();
            var result = importService.applyVerification(
                    uploadId,
                    user == null ? null : user.getUser().getLoginUserNo(),
                    force);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        // FinanceBusinessException (PER_SUPPLIER_MISMATCH 含む) は FinanceExceptionHandler で処理
    }

    // ---- 検証済み買掛金から MF CSV 出力（Excel 再アップロード不要）----

    /**
     * 検証済み(verificationResult=1 & mfExportEnabled=true)の買掛金サマリから
     * MF仕訳CSVを直接生成する。PAYABLE 行のみが含まれる点に注意。
     * <p>CSV 取引日列は 小田光締め日 = transactionMonth (前月20日) 固定。
     * 支払日は MF 側の銀行データ連携で自動付与されるため CSV には含めない。
     *
     * @param transactionMonth 対象取引月 (yyyy-MM-dd, 例 2026-01-20)
     */
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @GetMapping("/export-verified")
    public ResponseEntity<?> exportVerified(
            @RequestParam("transactionMonth") String transactionMonth,
            @AuthenticationPrincipal LoginUser user) {
        try {
            java.time.LocalDate txMonth = java.time.LocalDate.parse(transactionMonth);
            var result = importService.exportVerifiedCsv(
                    txMonth, user == null ? null : user.getUser().getLoginUserNo());

            String yyyymmdd = txMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fileName = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
            String asciiName = "payment_mf_" + yyyymmdd + ".csv"; // SF-C20: 日付付きで統一
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            // X-Skipped-Suppliers: 各 supplier 名を個別に encodeURIComponent し "|" で連結する。
            // supplier 名に "," が含まれるケースでパース崩れを起こさないよう、区切り文字はカンマ以外にする。
            // フロント側は split("|") → decodeURIComponent でデコードする。
            List<String> suppliers = result.getSkippedSuppliers() == null ? List.of()
                    : result.getSkippedSuppliers();
            String skippedHeader = suppliers.stream()
                    .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                    .reduce((a, b) -> a + "|" + b)
                    .orElse("");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded)
                    .header("X-Row-Count", String.valueOf(result.getRowCount()))
                    .header("X-Total-Amount", String.valueOf(result.getTotalAmount()))
                    .header("X-Skipped-Count", String.valueOf(suppliers.size()))
                    .header("X-Skipped-Suppliers", skippedHeader)
                    .body(result.getCsv());
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "日付形式が不正です (yyyy-MM-dd)"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        // IllegalStateException は FinanceExceptionHandler#handleIllegalState で 422 + 汎用メッセージ統一
    }

    /**
     * 検証済みCSV出力のプレビュー。ダイアログで件数確認 + 警告表示用。
     */
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @GetMapping("/export-verified/preview")
    public ResponseEntity<?> exportVerifiedPreview(
            @RequestParam("transactionMonth") String transactionMonth) {
        try {
            java.time.LocalDate txMonth = java.time.LocalDate.parse(transactionMonth);
            VerifiedExportPreviewResponse preview = importService.buildVerifiedExportPreview(txMonth);
            return ResponseEntity.ok(preview);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "日付形式が不正です (yyyy-MM-dd)"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 指定取引月の MF 補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) 一覧を返す。
     * 買掛金一覧の「MF補助行」タブ表示用。
     */
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @GetMapping("/aux-rows")
    public ResponseEntity<?> auxRows(
            @RequestParam("transactionMonth") String transactionMonth) {
        try {
            java.time.LocalDate txMonth = java.time.LocalDate.parse(transactionMonth);
            List<PaymentMfAuxRowResponse> rows = importService.listAuxRows(txMonth);
            return ResponseEntity.ok(rows);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "日付形式が不正です (yyyy-MM-dd)"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 履歴 ----

    @GetMapping("/history")
    public ResponseEntity<List<PaymentMfHistoryResponse>> history() {
        return ResponseEntity.ok(
                historyRepository.findByShopNoAndDelFlgOrderByTransferDateDescIdDesc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, "0")
                        .stream().map(PaymentMfHistoryResponse::from).toList());
    }

    @GetMapping("/history/{id}/csv")
    public ResponseEntity<?> historyCsv(@PathVariable Integer id) {
        try {
            byte[] csv = importService.getHistoryCsv(id);
            String yyyymmdd = importService.getHistoryTransferDate(id)
                    .map(d -> d.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")))
                    .orElse("history");
            String fileName = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"payment_mf_" + yyyymmdd + ".csv\"; filename*=UTF-8''" + encoded)
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

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/rules/{id}")
    public ResponseEntity<PaymentMfRuleResponse> updateRule(@PathVariable Integer id,
                                                            @Valid @RequestBody PaymentMfRuleRequest req,
                                                            @AuthenticationPrincipal LoginUser user) {
        return ResponseEntity.ok(PaymentMfRuleResponse.from(
                ruleService.update(id, req, user == null ? null : user.getUser().getLoginUserNo())));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Integer id,
                                        @AuthenticationPrincipal LoginUser user) {
        ruleService.delete(id, user == null ? null : user.getUser().getLoginUserNo());
        return ResponseEntity.noContent().build();
    }

    /**
     * PAYABLE ルールの payment_supplier_code を m_payment_supplier から一括補完。
     * dryRun=true でプレビューのみ、false で実際にDB更新。
     */
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/rules/backfill-codes")
    public ResponseEntity<?> backfillCodes(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @AuthenticationPrincipal LoginUser user) {
        var result = ruleService.backfillPaymentSupplierCodes(
                dryRun, user == null ? null : user.getUser().getLoginUserNo());
        return ResponseEntity.ok(result);
    }
}
