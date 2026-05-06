package jp.co.oda32.batch.finance.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.finance.AccountsPayableAggregationTasklet;
import jp.co.oda32.batch.finance.AccountsPayableSummaryInitTasklet;
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
 * 買掛金集計（集計処理のみ）バッチの設定クラス
 * 仕入データの集計のみを行い、SMILE支払情報の取込みや照合は行わない
 * Spring Boot 3.x / Spring Batch 5.x対応:
 * JobBuilderFactory, StepBuilderFactory の代わりに
 * JobRepository, PlatformTransactionManager を直接利用
 */
@Configuration
@RequiredArgsConstructor
@Log4j2
public class AccountsPayableAggregationConfig {

    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final AccountsPayableSummaryInitTasklet accountsPayableSummaryInitTasklet;
    @NonNull
    private final AccountsPayableAggregationTasklet accountsPayableAggregationTasklet;

    @Bean
    public JobExecutionListener accountsPayableAggregationListener() {
        return new JobStartEndListener();
    }

    /**
     * 買掛金集計（集計処理のみ）ジョブ
     * 注意：Bean名は「ジョブ名 + Job」の形式で定義すること
     * 例：accountsPayableAggregationJob
     * これにより、実行時に指定するジョブ名「accountsPayableAggregation」と連携する
     */
    @Bean
    public Job accountsPayableAggregationJob() {
        return new JobBuilder("accountsPayableAggregation", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(accountsPayableAggregationListener())
                .start(accountsPayableAggregationInitStep())   // 最初に買掛金サマリーテーブルの初期化ステップを実行
                .next(accountsPayableAggregationStep())
                .build();
    }

    /**
     * 買掛金サマリーテーブル初期化ステップ
     * （旧 {@code accountsPayableSummaryInitStep} と Bean 名衝突を避けるため、
     * Aggregation 系では {@code accountsPayableAggregationInitStep} に rename）
     */
    @Bean
    public Step accountsPayableAggregationInitStep() {
        return new StepBuilder("accountsPayableAggregationInitStep", jobRepository)
                .tasklet(accountsPayableSummaryInitTasklet, transactionManager)
                .build();
    }

    /**
     * 買掛金集計ステップ（集計処理のみ）
     */
    @Bean
    public Step accountsPayableAggregationStep() {
        return new StepBuilder("accountsPayableAggregationStep", jobRepository)
                .tasklet(accountsPayableAggregationTasklet, transactionManager)
                .build();
    }
}
