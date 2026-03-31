package jp.co.oda32.batch.finance.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.finance.AccountsReceivableToSalesJournalTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
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
 * 売掛金から売上帳への連携処理CSV出力バッチの設定クラス
 * Spring Boot 3.x / Spring Batch 5.x対応
 *
 * @author k_oda
 * @since 2025/05/15
 */
@Configuration
@RequiredArgsConstructor
public class SalesJournalIntegrationConfig {
    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final AccountsReceivableToSalesJournalTasklet accountsReceivableToSalesJournalTasklet;


    @Bean
    public Job salesJournalIntegrationJob() {
        return new JobBuilder("salesJournalIntegration", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(new JobStartEndListener())
                .flow(accountsReceivableToSalesJournalStep())
                .end()
                .build();
    }

    @Bean
    public Step accountsReceivableToSalesJournalStep() {
        return new StepBuilder("accountsReceivableToSalesJournalStep", jobRepository)
                .tasklet(accountsReceivableToSalesJournalTasklet, transactionManager)
                .build();
    }
}
