package jp.co.oda32.domain.model.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsPayableSummaryPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 20日締め買掛金テーブルのEntityクラス
 *
 * @author k_oda
 * @since 2024/09/10
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "t_accounts_payable_summary")
@IdClass(TAccountsPayableSummaryPK.class)
public class TAccountsPayableSummary {
    @Id
    @Column(name = "shop_no")
    private Integer shopNo;

    @Id
    @Column(name = "supplier_no")
    private Integer supplierNo;

    @Column(name = "supplier_code")
    private String supplierCode;

    @Id
    @Column(name = "transaction_month")
    private LocalDate transactionMonth;

    @Id
    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Column(name = "tax_included_amount")
    private BigDecimal taxIncludedAmount;

    @Column(name = "tax_excluded_amount")
    private BigDecimal taxExcludedAmount;

    @Column(name = "tax_included_amount_change")
    private BigDecimal taxIncludedAmountChange;

    @Column(name = "tax_excluded_amount_change")
    private BigDecimal taxExcludedAmountChange;

    // 検証結果のフラグを追加（1: 一致、0: 不一致、null: 検証なし）
    @Column(name = "verification_result")
    private Integer verificationResult;

    // SMILE支払額との差額
    @Column(name = "payment_difference")
    private BigDecimal paymentDifference;

    // マネーフォワードエクスポート可否フラグを追加
    @Column(name = "mf_export_enabled")
    private Boolean mfExportEnabled; // デフォルトではエクスポート可能

    // 手入力保護: trueならSMILE再検証バッチで上書きされない
    @Builder.Default
    @Column(name = "verified_manually", nullable = false)
    @ColumnDefault("false")
    private Boolean verifiedManually = false;

    // 検証時の備考（請求書番号・確認経緯など）
    @Column(name = "verification_note")
    private String verificationNote;

    /**
     * 振込実績で確定した集約金額 (税込)。
     *
     * <p><b>書込経路と挙動 (C3 2026-05-06 修正)</b>:
     * 本フィールドは正規アプリ経路で 3 つの書込経路があり、税率別行 (taxRate 違い) で同値か否かが異なる。
     * <ul>
     *   <li>{@code PaymentMfImportService.applyVerification}: 振込明細 Excel から
     *       supplier × transaction_month の集約値を <b>全税率行に同値</b> で書込
     *       (税率別の請求書内訳が Excel に存在しないため、集約値を冗長保持)。
     *       この経路では「同 (shop, supplier, transactionMonth) の全税率行で同値」が成立。</li>
     *   <li>{@code TAccountsPayableSummaryService.verify}: 単一 PK
     *       (shop, supplier, txMonth, taxRate) 行のみ UPDATE。
     *       この経路では税率別に <b>異なる値</b> が入る (手動 verify による税率別細分修正)。</li>
     *   <li>{@code ConsistencyReviewService.applyMfOverride}: MF journal の当月買掛金 debit を
     *       税率別に按分して書込。この経路でも税率別に <b>異なる値</b> が入る。</li>
     * </ul>
     *
     * <p><b>read 側のフォールバック</b>:
     * {@code PaymentMfImportService.sumVerifiedAmountForGroup} は「全行同値 → 代表値、不一致 → SUM」
     * フォールバックで両系をハンドリング。書込側で invariant を強制するのではなく、read 側
     * reconciliation logic として機能する。
     *
     * <p>仕訳生成での扱い:
     * <ul>
     *   <li><b>振込仕訳</b> (借方買掛金 / 貸方普通預金): 集約値として 1 度だけ参照 (税率不要、両方「対象外」)</li>
     *   <li><b>仕入仕訳</b> (借方仕入高 / 貸方買掛金): 税率別の {@link #verifiedAmountTaxExcluded} を税率ごとに参照</li>
     * </ul>
     *
     * <p><b>運用注意</b>:
     * applyVerification 経由で書込んだ後に verify / applyMfOverride で部分上書きすると、
     * 「全行同値前提」が崩れ read 側 SUM フォールバックに切り替わる。SUM フォールバックは
     * 単一税率しかない supplier では正しく動くが、税率混在 supplier では本来の集約値より
     * 多重計上される (例: 10% 行と 8% 行で異なる値 → SUM が集約値より大)。
     * 監査時は同 supplier の全税率行値を確認し、書込経路を識別すること。
     *
     * <p>関連:
     * <ul>
     *   <li>P1-03 案 D-2: 振込明細から per-supplier の値引/早払/送料を抽出して MF 仕訳に展開 (実装済)</li>
     *   <li>P1-09 案 D: 本フィールドの不変条件文書化 (V035)</li>
     *   <li>Codex Critical C3 (2026-05-06): V035 の絶対不変条件記述を、書込経路ごとの挙動記述に修正 (V039 + 本 Javadoc)</li>
     *   <li>中期課題: {@code verification_source} enum 列追加 ('BULK_VERIFICATION' / 'MANUAL_VERIFICATION' / 'MF_OVERRIDE')
     *       で write-side トレーサビリティを確立し、read 側でも経路別判定可能にする</li>
     *   <li>P1-09 将来 案 E (未実施): supplier × month の集約値テーブルと税率別 breakdown テーブルへの分離</li>
     * </ul>
     */
    @Column(name = "verified_amount")
    private BigDecimal verifiedAmount;

    /**
     * 検証値の書込経路 (G2-M1/M10, V040, 2026-05-06)。
     * <p>取り得る値:
     * <ul>
     *   <li>{@code "BULK_VERIFICATION"} ({@code FinanceConstants.VERIFICATION_SOURCE_BULK}):
     *       {@code PaymentMfImportService.applyVerification} 由来。全税率行同値の集約値。</li>
     *   <li>{@code "MANUAL_VERIFICATION"} ({@code FinanceConstants.VERIFICATION_SOURCE_MANUAL}):
     *       {@code TAccountsPayableSummaryService.verify} 由来。単一 PK のみ。税率別に異なる値。</li>
     *   <li>{@code "MF_OVERRIDE"} ({@code FinanceConstants.VERIFICATION_SOURCE_MF_OVERRIDE}):
     *       {@code ConsistencyReviewService.applyMfOverride} 由来。税率別按分。</li>
     *   <li>{@code null}: 未検証。{@code verified_manually=false}。</li>
     * </ul>
     *
     * <p><b>read 側の扱い</b>:
     * <ul>
     *   <li>{@code sumVerifiedAmountForGroup}: 全行 BULK なら代表値 1 度、それ以外 (MANUAL / MF_OVERRIDE / NULL 混在) は SUM。
     *       全行 BULK でも稀に同値でない場合 (DB 直接 UPDATE 等) は WARN ログ + SUM フォールバック。</li>
     *   <li>{@code applyVerification}: MANUAL 由来行は再 upload で上書きしない (UI 手入力保護)。</li>
     *   <li>{@code AccountsPayableResponse}: UI バッジ表示 (BULK/MANUAL) を本列で判定。</li>
     * </ul>
     *
     * <p>V040 backfill 時点では verification_note 接頭辞 + verified_manually で 3 種類に分類済。
     * 以後の書込は 3 経路 (applyVerification / verify / applyMfOverride) で本列を直接セットする。
     */
    @Column(name = "verification_source", length = 20)
    private String verificationSource;

    /**
     * 振込実績で確定した税抜金額 (税率別逆算)。
     * V026 (2026-04-23) で追加。{@link #verifiedAmount} (税込) を税率別に逆算した値で、
     * 同 supplier × month の税率別行で <b>異なる値</b> が入る (各行の {@code taxRate} で逆算)。
     *
     * <p>用途: MF CSV 仕入仕訳 (借方仕入高) の金額として使用。
     * {@code PayableBalanceCalculator.effectiveChangeTaxExcluded} 経由で 集計時に参照される。
     *
     * <p>計算式: {@code verifiedAmountTaxExcluded = verifiedAmount × 100 / (100 + taxRate)} (端数切捨)
     *
     * <p>注意: {@link #verifiedAmount} は同値だが、本フィールドは税率別に異なる。
     */
    @Column(name = "verified_amount_tax_excluded")
    private BigDecimal verifiedAmountTaxExcluded;

    /**
     * 振込明細 Excel 取込時の自動調整額 (= verifiedAmount - taxIncludedAmountChange、符号あり)。
     * 消費税丸め差等で ±100 円以内に自動合わせ込みされた金額。0 なら調整なし。
     * V026 で追加 (2026-04-23)。
     */
    @Builder.Default
    @Column(name = "auto_adjusted_amount", nullable = false)
    @ColumnDefault("0")
    private BigDecimal autoAdjustedAmount = BigDecimal.ZERO;

    /**
     * MF CSV 出力時の送金日 (CSV 取引日列に使う)。
     * Excel 振込明細取込 (applyVerification) で、行が属するセクションの送金日を記録する。
     * 5日払いセクション hit → 当月 5日。NULL 時は transactionMonth (締め日) にフォールバック。
     */
    @Column(name = "mf_transfer_date")
    private LocalDate mfTransferDate;

    /**
     * 前月末時点の累積残 (税込・符号あり)。
     * closing_balance = opening + effectiveChange は Entity には持たず DTO 層で算出。
     * 手動確定行でも常にバッチで上書きされる (change 列は保護、opening 列は繰越が絶対条件のため)。
     * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.2
     */
    @Builder.Default
    @Column(name = "opening_balance_tax_included", nullable = false)
    @ColumnDefault("0")
    private BigDecimal openingBalanceTaxIncluded = BigDecimal.ZERO;

    /**
     * 前月末時点の累積残 (税抜・符号あり)。
     */
    @Builder.Default
    @Column(name = "opening_balance_tax_excluded", nullable = false)
    @ColumnDefault("0")
    private BigDecimal openingBalanceTaxExcluded = BigDecimal.ZERO;

    /**
     * 当月完了した支払額 (税込)。
     * supplier 単位支払を税率別 change 比で按分。
     * 設計書: claudedocs/design-phase-b-prime-payment-settled.md §2.2
     * Phase B': closing = opening + change - payment_settled の算出要素。
     */
    @Builder.Default
    @Column(name = "payment_amount_settled_tax_included", nullable = false)
    @ColumnDefault("0")
    private BigDecimal paymentAmountSettledTaxIncluded = BigDecimal.ZERO;

    /**
     * 当月完了した支払額 (税抜)。change_excl 比で按分。
     */
    @Builder.Default
    @Column(name = "payment_amount_settled_tax_excluded", nullable = false)
    @ColumnDefault("0")
    private BigDecimal paymentAmountSettledTaxExcluded = BigDecimal.ZERO;

    /**
     * payment-only 行フラグ。
     * 当月 change=0 だが前月支払があった supplier のために生成された行で、
     * stale-delete 対象から除外するための目印。
     */
    @Builder.Default
    @Column(name = "is_payment_only", nullable = false)
    @ColumnDefault("false")
    private Boolean isPaymentOnly = false;
}
