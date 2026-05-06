package jp.co.oda32.domain.service.finance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;

/**
 * P1-09 案 D / G2-M1 (V040, 2026-05-06): verified_amount 集計の経路別挙動テスト。
 *
 * <p>{@code sumVerifiedAmountForGroup} は {@code verification_source} 列で挙動を分岐する:
 * <ul>
 *   <li>全行 BULK_VERIFICATION → 代表値 (集約値の冗長保持)</li>
 *   <li>1 行でも MANUAL_VERIFICATION / MF_OVERRIDE / NULL → SUM (税率別の合計)</li>
 *   <li>全行 BULK で値が不一致 → WARN ログ + SUM フォールバック (DB 異常検知)</li>
 * </ul>
 * 本テストは「金額パターン推定」から「source 列明示判定」への切替えを locked-in する。
 */
class PaymentMfImportServiceVerifiedAmountTest {

    @Test
    void 全行BULK_全税率行同値時_代表値1度のみ集計() {
        // applyVerification 由来: supplier × month で 3 税率行、全て verified_amount = 1,000,000
        List<TAccountsPayableSummary> group = List.of(
            buildBulkRow(BigDecimal.valueOf(10), BigDecimal.valueOf(1_000_000)),
            buildBulkRow(BigDecimal.valueOf(8),  BigDecimal.valueOf(1_000_000)),
            buildBulkRow(BigDecimal.valueOf(8),  BigDecimal.valueOf(1_000_000))
        );

        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);

        // 全行 BULK + 同値 → 代表値として 1,000,000 を返す (3,000,000 と二重計上しない)
        assertThat(result).isEqualTo(1_000_000L);
    }

    @Test
    void 全行BULK_不一致時_SUMフォールバック_過大計上検知用() {
        // 不変条件違反: 全行 BULK だが 1 行だけ手動 SQL UPDATE 等で異なる値
        List<TAccountsPayableSummary> group = List.of(
            buildBulkRow(BigDecimal.valueOf(10), BigDecimal.valueOf(1_000_000)),
            buildBulkRow(BigDecimal.valueOf(8),  BigDecimal.valueOf(500_000)),  // ← 不正
            buildBulkRow(BigDecimal.valueOf(8),  BigDecimal.valueOf(1_000_000))
        );

        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);

        // 全行 BULK + 不一致 → WARN + SUM フォールバック (= 2,500,000)
        // これは「BULK 不変条件が崩れた」検知用の安全側挙動。
        assertThat(result).isEqualTo(2_500_000L);
    }

    @Test
    void verifiedAmount_null時はtaxIncludedAmountChangeフォールバック() {
        // verified_amount=null の場合は source も NULL (未検証行) → SUM 経路
        List<TAccountsPayableSummary> group = List.of(
            buildRow(BigDecimal.valueOf(10), null, BigDecimal.valueOf(800_000), null),
            buildRow(BigDecimal.valueOf(8),  null, BigDecimal.valueOf(200_000), null)
        );

        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);

        // source NULL → SUM 経路、各行 verified=null → taxIncludedAmountChange を採用
        assertThat(result).isEqualTo(1_000_000L);
    }

    /**
     * G2-M1 ケース 3: MANUAL_VERIFICATION で偶然全行同値になる場合。
     * 旧実装は「金額パターン全行同値 → 代表値」と推定し過少計上していた。
     * 新実装は source 列で MANUAL を識別し、税率別 SUM を返す。
     */
    @Test
    void MANUAL混在_偶然全行同値でもSUM() {
        // ユーザが UI で 8% 行に 50,000、10% 行に 50,000 を verify。total は 100,000 のはず
        List<TAccountsPayableSummary> group = List.of(
            buildManualRow(BigDecimal.valueOf(10), BigDecimal.valueOf(50_000)),
            buildManualRow(BigDecimal.valueOf(8),  BigDecimal.valueOf(50_000))
        );

        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);

        // MANUAL 由来 → SUM = 100,000 (旧実装は 50,000 と過少計上)
        assertThat(result).isEqualTo(100_000L);
    }

    /**
     * G2-M1 ケース 4: BULK 後に単行修正 (BULK + MANUAL/MF_OVERRIDE 混在) のケース。
     * 1 行でも非 BULK が混じれば SUM 経路を取る。
     */
    @Test
    void BULK後MF_OVERRIDE混在_SUM() {
        // applyVerification 後に整合性レポートで 1 行だけ MF_APPLY された状況
        List<TAccountsPayableSummary> group = List.of(
            buildBulkRow(BigDecimal.valueOf(10), BigDecimal.valueOf(100_000)),
            buildRow(BigDecimal.valueOf(8), BigDecimal.valueOf(99_000), null,
                     FinanceConstants.VERIFICATION_SOURCE_MF_OVERRIDE)
        );

        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);

        // BULK + MF_OVERRIDE 混在 → SUM = 199,000 (代表値 100,000 ではない)
        assertThat(result).isEqualTo(199_000L);
    }

    /**
     * G2-M1 ケース 5: 単一税率 supplier の MANUAL 検証。
     * 旧実装でも新実装でも結果は同じだが、source 経由の SUM 経路で正しく 1 行値を返すことを確認。
     */
    @Test
    void 単一税率MANUAL_SUM経路で1行値() {
        List<TAccountsPayableSummary> group = List.of(
            buildManualRow(BigDecimal.valueOf(10), BigDecimal.valueOf(75_000))
        );

        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);

        assertThat(result).isEqualTo(75_000L);
    }

    private TAccountsPayableSummary buildBulkRow(BigDecimal taxRate, BigDecimal verifiedAmount) {
        return buildRow(taxRate, verifiedAmount, null, FinanceConstants.VERIFICATION_SOURCE_BULK);
    }

    private TAccountsPayableSummary buildManualRow(BigDecimal taxRate, BigDecimal verifiedAmount) {
        return buildRow(taxRate, verifiedAmount, null, FinanceConstants.VERIFICATION_SOURCE_MANUAL);
    }

    private TAccountsPayableSummary buildRow(BigDecimal taxRate, BigDecimal verifiedAmount,
                                              BigDecimal change, String source) {
        TAccountsPayableSummary s = new TAccountsPayableSummary();
        s.setTaxRate(taxRate);
        s.setVerifiedAmount(verifiedAmount);
        s.setTaxIncludedAmountChange(change);
        s.setVerificationSource(source);
        return s;
    }
}
