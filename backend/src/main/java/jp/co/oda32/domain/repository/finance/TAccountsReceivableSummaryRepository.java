package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsReceivableSummaryPK;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 20日締め売掛金テーブルのリポジトリクラス
 *
 * @author k_oda
 * @since 2024/08/31
 * @modified 2025/04/30 - 年月に該当する締め日データを取得するメソッドを追加
 * @modified 2026/04/17 - 検証フィルタ対応のため Specification 実行を追加
 */
@Repository
public interface TAccountsReceivableSummaryRepository
        extends JpaRepository<TAccountsReceivableSummary, TAccountsReceivableSummaryPK>,
                JpaSpecificationExecutor<TAccountsReceivableSummary> {
    /**
     * 指定された取引月に対応するTAccountsReceivableSummaryのリストを取得します。
     *
     * @param transactionMonth 取引月
     * @return 取引月に対応するTAccountsReceivableSummaryのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonth(LocalDate transactionMonth);

    /**
     * 指定された取引月リストのいずれかに一致する売掛金サマリーを取得します。
     *
     * @param transactionMonths 取引月のリスト
     * @return 売掛金サマリーのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonthIn(List<LocalDate> transactionMonths);

    /**
     * 指定された日付リストのいずれかに一致し、金額が変更されている売掛金サマリーを取得します。
     *
     * @param transactionMonths 取引日のリスト
     * @return 金額が変更されている売掛金サマリーのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonthInAndTaxIncludedAmountChangeIsNotNull(List<LocalDate> transactionMonths);

    TAccountsReceivableSummary getByShopNoAndPartnerNoAndTransactionMonthAndTaxRateAndIsOtakeGarbageBag(
            int shopNo, int partnerNo, LocalDate transactionMonth, BigDecimal taxRate, boolean isOtakeGarbageBag);

    /**
     * 指定された日付範囲内の売掛金サマリーを取得します。
     *
     * @param fromDate 開始日（この日を含む）
     * @param toDate 終了日（この日を含む）
     * @return 売掛金サマリーのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonthBetween(LocalDate fromDate, LocalDate toDate);

    /**
     * 指定された日付範囲内かつ MF出力可否フラグが一致する売掛金サマリーを取得します。
     *
     * @param fromDate        開始日（この日を含む）
     * @param toDate          終了日（この日を含む）
     * @param mfExportEnabled MF出力可否フラグ
     * @return 売掛金サマリーのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonthBetweenAndMfExportEnabled(
            LocalDate fromDate, LocalDate toDate, Boolean mfExportEnabled);

    /**
     * 指定された店舗・日付範囲内の売掛金サマリーを取得します。
     */
    List<TAccountsReceivableSummary> findByShopNoAndTransactionMonthBetween(
            Integer shopNo, LocalDate fromDate, LocalDate toDate);
}
