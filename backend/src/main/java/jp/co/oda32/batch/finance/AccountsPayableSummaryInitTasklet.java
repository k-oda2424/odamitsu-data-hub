package jp.co.oda32.batch.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 買掛金サマリーテーブルの初期化用タスクレット
 * バッチ処理の最初に差額情報と連携可否フラグをリセットします
 *
 * @author k_oda
 * @since 2025/05/13
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableSummaryInitTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // targetDate を LocalDate に変換
        LocalDate startDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 期間を設定（前月21日から当月20日まで）
        LocalDate periodStartDate = YearMonth.from(startDate).minusMonths(1).atDay(21); // 前月21日
        LocalDate periodEndDate = YearMonth.from(startDate).atDay(20); // 当月20日

        log.info("買掛金サマリーテーブルの差額情報と連携可否フラグを初期化します。対象期間: {} 〜 {}",
                periodStartDate, periodEndDate);

        // 対象期間のデータの差額と連携可否をリセット
        // - shop_no=1 のみ対象（shop_no=2 の手動買掛レコードに影響させない）
        // - verified_manually=true の手動確定レコードは保護（SmilePaymentVerifier もスキップする）
        String sql = "UPDATE t_accounts_payable_summary " +
                "SET payment_difference = NULL, verification_result = NULL, mf_export_enabled = FALSE " +
                "WHERE transaction_month = ? " +
                "  AND shop_no = 1 " +
                "  AND (verified_manually IS NULL OR verified_manually = FALSE)";

        int updatedRows = jdbcTemplate.update(sql, java.sql.Date.valueOf(periodEndDate));

        log.info("買掛金サマリーテーブルの初期化が完了しました。更新件数: {}", updatedRows);

        return RepeatStatus.FINISHED;
    }
}
