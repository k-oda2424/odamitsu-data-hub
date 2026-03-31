package jp.co.oda32.batch.finance.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.finance.AccountsPayableToPurchaseJournalTasklet;
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
 * 買掛金から仕入帳への連携処理バッチの設定クラス
 * Spring Boot 3.x / Spring Batch 5.x対応:
 * JobBuilderFactory, StepBuilderFactory の代わりに
 * JobRepository, PlatformTransactionManager を直接利用
 *
 * @author k_oda
 * @since 2025/05/09
 */
@Configuration
@RequiredArgsConstructor
@Log4j2
public class PurchaseJournalIntegrationConfig {

    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final AccountsPayableToPurchaseJournalTasklet accountsPayableToPurchaseJournalTasklet;

    @Bean
    public JobExecutionListener purchaseJournalIntegrationListener() {
        return new JobStartEndListener();
    }

    /**
     * 買掛金から仕入帳への連携処理ジョブ
     * 注意：Bean名は「ジョブ名 + Job」の形式で定義すること
     * 例：purchaseJournalIntegrationJob
     * これにより、実行時に指定するジョブ名「purchaseJournalIntegration」と連携する
     */
    @Bean
    public Job purchaseJournalIntegrationJob() {
        return new JobBuilder("purchaseJournalIntegration", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(purchaseJournalIntegrationListener())
                .flow(accountsPayableToPurchaseJournalStep())
                .end()
                .build();
    }

    /**
     * 買掛金から仕入帳への連携処理ステップ
     */
    @Bean
    public Step accountsPayableToPurchaseJournalStep() {
        return new StepBuilder("accountsPayableToPurchaseJournalStep", jobRepository)
                .tasklet(accountsPayableToPurchaseJournalTasklet, transactionManager)
                .build();
    }
}
