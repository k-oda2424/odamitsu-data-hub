package jp.co.oda32.domain.service.finance;

/**
 * 振込明細 Excel のセクション。
 * <p>G2-M3 (2026-05-06): 旧 {@code afterTotal} 真偽値フラグを廃止し、
 * 5日払い / 20日払いセクションを明示的にモデル化する。
 *
 * <ul>
 *   <li>{@link #PAYMENT_5TH} — 月初 5 日締め支払 (= 月前半セクション)。Excel 先頭側。</li>
 *   <li>{@link #PAYMENT_20TH} — 20 日締め支払 (= 月後半セクション)。Excel 後半側。</li>
 * </ul>
 *
 * <p>Excel は通常 5日払い 明細 → 5日払い 合計 → 20日払い 明細 → 20日払い 合計 の順。
 * 5日払いのみの月もある (= 20日払いセクションは空)。20日払いのみの月は運用上想定しない。
 *
 * <p>旧実装は「合計行を 1 個だけキャプチャしたら以後は全て afterTotal=true」だったため、
 * 20日払いセクションの合計行が捨てられて整合性チェック (chk1/chk3) が構造的にズレていた
 * (Codex Major G2-M3)。本 enum 導入により、合計行ごとに section 別 summary を保持できる。
 */
public enum PaymentMfSection {
    PAYMENT_5TH,
    PAYMENT_20TH
}
