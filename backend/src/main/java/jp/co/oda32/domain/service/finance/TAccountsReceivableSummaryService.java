package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.repository.finance.TAccountsReceivableSummaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

/**
 * 20日締め売掛金テーブルのサービスクラス
 *
 * @author k_oda
 * @since 2024/08/31
 * @modified 2025/04/30 - 指定年月の締め月データを取得するメソッドを追加
 */
@Service
public class TAccountsReceivableSummaryService {

    private final TAccountsReceivableSummaryRepository repository;

    @Autowired
    public TAccountsReceivableSummaryService(TAccountsReceivableSummaryRepository repository) {
        this.repository = repository;
    }

    public List<TAccountsReceivableSummary> findAll() {
        return repository.findAll();
    }

    public TAccountsReceivableSummary save(TAccountsReceivableSummary summary) {
        return repository.save(summary);
    }

    /**
     * 指定された取引月に対応するTAccountsReceivableSummaryのリストを取得します。
     *
     * @param transactionMonth 取引月
     * @return 取引月に対応するTAccountsReceivableSummaryのリスト
     */
    public List<TAccountsReceivableSummary> findByTransactionMonth(LocalDate transactionMonth) {
        return repository.findByTransactionMonth(transactionMonth);
    }

    /**
     * 指定された年月で締め月が該当する売掛金サマリーを取得します。
     * 締め日に応じて取引日(transactionMonth)が異なるため、年月をベースに対象データを特定します。
     *
     * @param yearMonth 対象年月（例：2025年2月）
     * @return 売掛金サマリーのリスト
     */
    public List<TAccountsReceivableSummary> findByCutoffYearMonth(YearMonth yearMonth) {
        // 指定年月の月末日を取得
        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        // 15日締めの場合は前月16日～当月15日が対象
        LocalDate day15 = yearMonth.atDay(15);

        // 20日締めの場合は前月21日～当月20日が対象
        LocalDate day20 = yearMonth.atDay(20);

        // 月末締め(0)と都度払い(-1)の場合は当月1日～当月末日が対象
        LocalDate firstDay = yearMonth.atDay(1);

        // 注：transactionMonthが締め日に対応する値になっている前提
        return repository.findByTransactionMonthIn(Arrays.asList(day15, day20, endOfMonth));
    }

    public TAccountsReceivableSummary getByPK(int shopNo, int partnerNo, LocalDate transactionMonth, BigDecimal taxRate, boolean isOtakeGarbageBag) {
        return repository.getByShopNoAndPartnerNoAndTransactionMonthAndTaxRateAndIsOtakeGarbageBag(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
    }

    /**
     * 指定された日付範囲内の売掛金サマリーを取得します。
     *
     * @param fromDate 開始日（この日を含む）
     * @param toDate 終了日（この日を含む）
     * @return 売掛金サマリーのリスト
     */
    public List<TAccountsReceivableSummary> findByDateRange(LocalDate fromDate, LocalDate toDate) {
        return repository.findByTransactionMonthBetween(fromDate, toDate);
    }

}
