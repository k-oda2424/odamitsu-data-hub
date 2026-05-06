package jp.co.oda32.domain.service.finance.mf;

/**
 * MF tenant binding 不一致 (DESIGN-DECISION P1-01 / Cluster F DD-F-04)。
 * <p>
 * 認可済 client に保存された {@code mf_tenant_id} と、callback / refresh 後に
 * 取得した tenant id が一致しない場合に throw する。
 * 別会社 MF を誤って認可した／別企業のクライアント情報で再認可した、などを示す。
 * <p>
 * Controller では 409 (CONFLICT) で返却する想定。{@link MfReAuthRequiredException}
 * と区別したいため別例外型にする (再認可するだけでは復旧せず、tenant 解除手順が必要)。
 *
 * @since 2026-05-04 (P1-01)
 */
public class MfTenantMismatchException extends RuntimeException {

    /** 既存 client に保存されている tenant id (バインド済み)。 */
    private final String boundTenantId;

    /** 今回 MF API から取得した tenant id (新規/不一致)。 */
    private final String observedTenantId;

    public MfTenantMismatchException(String boundTenantId, String observedTenantId, String message) {
        super(message);
        this.boundTenantId = boundTenantId;
        this.observedTenantId = observedTenantId;
    }

    public String getBoundTenantId() {
        return boundTenantId;
    }

    public String getObservedTenantId() {
        return observedTenantId;
    }
}
