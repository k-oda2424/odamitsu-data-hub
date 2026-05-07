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

    /**
     * Codex Major #4 (2026-05-06): {@code force=true} 指定時の<b>必須</b>業務理由文字列。
     * <p>強制反映は per-supplier 1 円整合性違反を許容する破壊的操作のため、
     * audit log に「なぜ承認したか」を残す目的で必須化する。空文字 / null で
     * {@code force=true} を送ると {@link jp.co.oda32.exception.FinanceBusinessException}
     * (code=FORCE_REASON_REQUIRED) で 400 拒否される。
     * <p>{@code force=false} の場合は無視される。
     * <p>運用 runbook (= 二段認可の代替): 承認前に最低 2 名で内容確認のうえ、
     * 担当者名 + 承認者名 + 業務上の理由をこの文字列に含める
     * (例: {@code "承認: tanaka, 確認: yamada, 理由: 仕入先X側の請求書送付遅延、振込済"} )。
     */
    private String forceReason;
}
