package jp.co.oda32.api.finance;

import jp.co.oda32.domain.service.finance.mf.MfTenantBindingFailedException;
import jp.co.oda32.dto.finance.ErrorResponse;
import jp.co.oda32.exception.FinanceBusinessException;
import jp.co.oda32.exception.FinanceInternalException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5: {@link FinanceExceptionHandler} の例外マッピング検証。
 *
 * <p>handler メソッドを直接呼ぶ単体テスト (MockMvc 不使用)。
 * Spring 起動コスト 0、handler ロジックの規約を locked-in する。
 *
 * @since 2026-05-04 (T5)
 */
class FinanceExceptionHandlerTest {

    private final FinanceExceptionHandler handler = new FinanceExceptionHandler();

    @Test
    void FinanceBusinessException_400_と元メッセージを返す() {
        FinanceBusinessException ex = new FinanceBusinessException("未登録の送り先があります（3件）");

        ResponseEntity<ErrorResponse> response = handler.handleFinanceBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("未登録の送り先があります（3件）");
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_ERROR");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void FinanceBusinessException_errorCode指定時_codeに反映される() {
        FinanceBusinessException ex = new FinanceBusinessException(
                "対象月にデータがありません", "NO_DATA");

        ResponseEntity<ErrorResponse> response = handler.handleFinanceBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("対象月にデータがありません");
        assertThat(response.getBody().code()).isEqualTo("NO_DATA");
    }

    @Test
    void FinanceInternalException_422と汎用化メッセージを返す() {
        // 機微情報を含むメッセージ (内部 supplier_no 露出など)
        FinanceInternalException ex = new FinanceInternalException(
                "買掛金集計の taxRate が null です: shopNo=1, supplierNo=12345");

        ResponseEntity<ErrorResponse> response = handler.handleFinanceInternal(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        // client には内部詳細が漏れない
        assertThat(response.getBody().message()).isEqualTo("内部エラーが発生しました");
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        // 元メッセージは body に含まれない (情報漏洩防止)
        assertThat(response.getBody().message()).doesNotContain("supplierNo");
        assertThat(response.getBody().message()).doesNotContain("12345");
    }

    @Test
    void IllegalStateException_既存ハンドラと同じ422汎用化_後方互換確認() {
        IllegalStateException ex = new IllegalStateException("内部 detail (露出させたくない)");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("内部エラーが発生しました");
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        // 既存挙動 (handleIllegalState) と FinanceInternalException が同じ汎用化規約であること
    }

    @Test
    void MfTenantBindingFailedException_503と汎用化メッセージを返す() {
        // G1-M3: 未バインド client 強制 binding 失敗時 → 503 + MF_TENANT_BINDING_FAILED
        MfTenantBindingFailedException ex = new MfTenantBindingFailedException(
                "未バインド client の tenant 取得に失敗しました clientId=1",
                new RuntimeException("upstream 503"));

        ResponseEntity<ErrorResponse> response = handler.handleMfTenantBindingFailed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MF_TENANT_BINDING_FAILED");
        // client には汎用メッセージのみ (内部詳細は log のみ)
        assertThat(response.getBody().message()).contains("MF テナント情報の取得に失敗");
        assertThat(response.getBody().message()).doesNotContain("clientId=1");
        assertThat(response.getBody().message()).doesNotContain("upstream");
    }

    @Test
    void FinanceBusinessException_cause指定コンストラクタ_messageが取れる() {
        Throwable cause = new RuntimeException("low level cause");
        FinanceBusinessException ex = new FinanceBusinessException("外側メッセージ", cause);

        ResponseEntity<ErrorResponse> response = handler.handleFinanceBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("外側メッセージ");
        // cause は client に漏れない (Throwable.getCause だけ)
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
