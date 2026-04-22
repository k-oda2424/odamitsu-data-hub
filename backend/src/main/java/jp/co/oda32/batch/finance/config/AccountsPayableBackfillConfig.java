package jp.co.oda32.batch.finance.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.finance.AccountsPayableBackfillTasklet;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 買掛金累積残 opening_balance の過去データ再集計バッチ設定。
 * <p>
 * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.4
 * <p>
 * 呼び出し:
 * <pre>
 *   --job.name=accountsPayableBackfill fromMonth=2025-06-20 toMonth=2026-03-20
 * </pre>
 */
@Configuration
@RequiredArgsConstructor
@Log4j2
public class AccountsPayableBackfillConfig {

    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final AccountsPayableBackfillTasklet accountsPayableBackfillTasklet;

    @Bean
    public JobExecutionListener accountsPayableBackfillListener() {
        return new JobStartEndListener();
    }

    @Bean
    public Job accountsPayableBackfillJob() {
        return new JobBuilder("accountsPayableBackfill", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(accountsPayableBackfillListener())
                .start(accountsPayableBackfillStep())
                .build();
    }

    @Bean
    public Step accountsPayableBackfillStep() {
        return new StepBuilder("accountsPayableBackfillStep", jobRepository)
                .tasklet(accountsPayableBackfillTasklet, transactionManager)
                .build();
    }
}
