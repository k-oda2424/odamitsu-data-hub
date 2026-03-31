package jp.co.oda32.batch.finance.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.finance.TAccountsReceivableSummaryTasklet;
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
 * 売掛金集計処理バッチの設定クラス
 * Spring Boot 3.x / Spring Batch 5.x対応
 *
 * @author k_oda
 * @modified 2025/05/15 - Configクラスに変更
 * @since 2024/09/01
 */
@Configuration
@RequiredArgsConstructor
public class AccountsReceivableSummaryConfig {
    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final TAccountsReceivableSummaryTasklet tAccountsReceivableSummaryTasklet;

    @Bean
    public Job accountsReceivableSummaryJob() {
        return new JobBuilder("accountsReceivableSummary", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(new JobStartEndListener())
                .flow(tAccountsReceivableSummaryStep())
                .end()
                .build();
    }

    @Bean
    public Step tAccountsReceivableSummaryStep() {
        return new StepBuilder("tAccountsReceivableSummaryStep", jobRepository)
                .tasklet(tAccountsReceivableSummaryTasklet, transactionManager)
                .build();
    }
}
