package jp.co.oda32.dto.finance.paymentmf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * G2-M2 (2026-05-06): 振込明細 Excel の検証反映 ({@code applyVerification}) リクエスト DTO。
 *
 * <p>従来 {@code POST /api/v1/finance/payment-mf/verify/{uploadId}} はボディ無しで呼べたが、
 * per-supplier 1 円整合性違反 ({@code perSupplierMismatches}) が出ていてもサーバー側で
 * ブロックしていなかったため、Excel 入力ミスがそのまま手動確定 + MF CSV に流れる事故が
 * あり得た。
 *
 * <p>本 DTO により以下の運用に変更:
 * <ul>
 *   <li>{@code force=false} (既定): per-supplier 1 円不一致が 1 件でもあれば 422 でブロック。
 *       UI 側は preview 画面で違反一覧を確認し、Excel を修正して再アップロードする。</li>
 *   <li>{@code force=true}: 違反を許容して反映。{@code finance_audit_log.reason} に
 *       {@code FORCE_APPLIED: per-supplier mismatches=...} を補足記録する。</li>
 * </ul>
 *
 * <p>ボディ省略 (旧 client) の場合は {@code force=false} 扱いとなる
 * ({@code @RequestBody(required=false)} で受ける)。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMfApplyRequest {

    /**
     * per-supplier 1 円不一致を許容して反映するかどうか。
     * <ul>
     *   <li>{@code false} (既定): 違反 1 件以上で 422 ブロック</li>
     *   <li>{@code true}: 違反を許容して反映 + audit log に reason 記録</li>
     * </ul>
     */
    @Builder.Default
    private boolean force = false;
}
