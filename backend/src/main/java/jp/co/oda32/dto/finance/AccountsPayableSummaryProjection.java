package jp.co.oda32.dto.finance;

import java.math.BigDecimal;

/**
 * {@link jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository#aggregateSummary}
 * の集計結果を 1 行で受け取る projection。
 * <p>
 * 旧実装は {@code findAll(spec)} で全行をロードしてからアプリ側で count / sum していたため、
 * 行数増加に対する性能劣化が見えていた (SF-24)。
 *
 * @param totalCount               対象行 (transactionMonth × shopNo) の総件数
 * @param unverifiedCount          verificationResult IS NULL の件数
 * @param unmatchedCount           verificationResult = 0 (不一致) の件数
 * @param matchedCount             verificationResult = 1 (一致) の件数
 * @param unmatchedDifferenceSum   verificationResult = 0 行の paymentDifference 合計
 *
 * @since 2026-05-04
 */
public record AccountsPayableSummaryProjection(
        Long totalCount,
        Long unverifiedCount,
        Long unmatchedCount,
        Long matchedCount,
        BigDecimal unmatchedDifferenceSum
) {}
