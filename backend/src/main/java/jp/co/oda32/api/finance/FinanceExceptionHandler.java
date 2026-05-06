package jp.co.oda32.api.finance;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfScopeInsufficientException;
import jp.co.oda32.domain.service.finance.mf.MfTenantBindingFailedException;
import jp.co.oda32.domain.service.finance.mf.MfTenantMismatchException;
import jp.co.oda32.dto.finance.ErrorResponse;
import jp.co.oda32.exception.FinanceBusinessException;
import jp.co.oda32.exception.FinanceInternalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Finance パッケージ ({@code jp.co.oda32.api.finance}) 内の Controller でのみ有効な例外ハンドラ。
 * <p>
 * グローバルな {@link jp.co.oda32.exception.GlobalExceptionHandler} より優先して評価される
 * （Spring の {@code @ControllerAdvice} は basePackages 一致が優先されるため）。
 * <p>
 * 旧来 try/catch で各 endpoint が個別に組み立てていた MF 認証/権限/計算失敗系のレスポンスを集約する。
 *
 * @since 2026-05-04 (SF-25)
 */
@Slf4j
@RestControllerAdvice(basePackages = "jp.co.oda32.api.finance")
public class FinanceExceptionHandler {

    /** MF refresh_token 失効など、ユーザー再認可が必要な状態 → 401 */
    @ExceptionHandler(MfReAuthRequiredException.class)
    public ResponseEntity<ErrorResponse> handleMfReAuthRequired(MfReAuthRequiredException ex) {
        log.warn("MF re-auth required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(ex.getMessage(), "MF_REAUTH_REQUIRED"));
    }

    /** MF API 認可スコープ不足 → 403 */
    @ExceptionHandler(MfScopeInsufficientException.class)
    public ResponseEntity<ErrorResponse> handleMfScopeInsufficient(MfScopeInsufficientException ex) {
        log.warn("MF scope insufficient (requiredScope={}): {}", ex.getRequiredScope(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.withScope(ex.getMessage(), "MF_SCOPE_INSUFFICIENT", ex.getRequiredScope()));
    }

    /**
     * MF tenant binding 不一致 (P1-01 / DD-F-04) → 409 CONFLICT。
     * <p>
     * 別会社 MF を誤って認可した場合などに発生する。再認可するだけでは復旧せず、
     * 旧連携を一旦「切断」してから再認可する必要があるため、{@link MfReAuthRequiredException}
     * (401) と区別して 409 を返す。client にはバインド済 tenant id / 観測 tenant id も
     * 含むメッセージを返す (運用者が状況把握できるよう)。
     */
    @ExceptionHandler(MfTenantMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMfTenantMismatch(MfTenantMismatchException ex) {
        log.error("MF tenant mismatch detected: bound={} observed={}",
                ex.getBoundTenantId(), ex.getObservedTenantId());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getMessage(), "MF_TENANT_MISMATCH"));
    }

    /**
     * MF tenant 強制バインド失敗 (G1-M3) → 503 SERVICE_UNAVAILABLE。
     * <p>
     * P1-01 導入前から認可済みの client (mf_tenant_id IS NULL) に対して
     * {@link jp.co.oda32.domain.service.finance.mf.MfOauthService#getValidAccessToken()}
     * が強制 binding を試行したが、{@code /v2/tenant} の fetch に失敗したケース。
     * MF API が一時的に応答しない / scope 不足等が想定要因のため、業務 API 自体は
     * 短時間で再試行可能 = 503 が適切 ({@link MfTenantMismatchException} の 409 と区別)。
     * client には汎用メッセージのみ返し、詳細は server log に記録する。
     */
    @ExceptionHandler(MfTenantBindingFailedException.class)
    public ResponseEntity<ErrorResponse> handleMfTenantBindingFailed(MfTenantBindingFailedException ex) {
        log.warn("MF tenant binding failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(
                        "MF テナント情報の取得に失敗しました。MF 連携設定を確認してください。",
                        "MF_TENANT_BINDING_FAILED"));
    }

    /**
     * 整合性レポート等、計算ロジック内部で発生した状態異常 → 422 (UNPROCESSABLE_ENTITY)。
     * <p>
     * 旧 endpoint との互換性 (status code drift 防止) のため 422 を返す。
     * 500 にすると client 側のエラーハンドリング (リトライ判定等) が変わってしまうため注意。
     * <p>
     * <strong>セキュリティ注意 (MA-01)</strong>: Finance パッケージ内 Service の {@link IllegalStateException}
     * メッセージには金額や CSV 行情報など内部詳細が含まれることがあるため、client には汎用メッセージのみ返却し、
     * 元のメッセージは log のみに記録する。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("Finance IllegalStateException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("内部エラーが発生しました", "INTERNAL_ERROR"));
    }

    /**
     * Finance 業務例外 → 400 Bad Request。
     * <p>
     * message はユーザー向けに作られているのでそのまま client に返す。
     * code は呼び出し元が指定していれば使い、未指定なら "BUSINESS_ERROR"。
     *
     * <p>G2-M2 (2026-05-06): {@link FinanceConstants#ERROR_CODE_PER_SUPPLIER_MISMATCH}
     * (買掛仕入 MF 振込明細の per-supplier 1 円不一致) は
     * 「クライアントが {@code force=true} を明示すれば突破できる業務不整合」のため、
     * 検証データの状態異常を示す 422 Unprocessable Entity にマップする。
     * その他の業務例外は従来通り 400 のまま。
     *
     * @since 2026-05-04 (T5)
     */
    @ExceptionHandler(FinanceBusinessException.class)
    public ResponseEntity<ErrorResponse> handleFinanceBusiness(FinanceBusinessException ex) {
        log.info("Finance business exception: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        String code = ex.getErrorCode() != null ? ex.getErrorCode() : "BUSINESS_ERROR";
        HttpStatus status = FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH.equals(code)
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(ex.getMessage(), code));
    }

    /**
     * Finance 内部例外 → 422 Unprocessable Entity。
     * <p>
     * 機微情報を含む可能性があるため client には汎用化メッセージのみ返す。
     * 詳細メッセージはサーバーログのみ記録 (情報漏洩防止)。
     *
     * @since 2026-05-04 (T5)
     */
    @ExceptionHandler(FinanceInternalException.class)
    public ResponseEntity<ErrorResponse> handleFinanceInternal(FinanceInternalException ex) {
        log.warn("Finance internal exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("内部エラーが発生しました", "INTERNAL_ERROR"));
    }
}
