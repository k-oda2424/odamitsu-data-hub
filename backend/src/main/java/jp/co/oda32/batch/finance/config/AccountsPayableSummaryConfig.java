package jp.co.oda32.batch.finance.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.finance.AccountsPayableSummaryInitTasklet;
import jp.co.oda32.batch.finance.AccountsPayableSummaryTasklet;
import jp.co.oda32.batch.finance.AccountsPayableVerificationReportTasklet;
import jp.co.oda32.batch.smile.*;
import jp.co.oda32.domain.model.smile.WSmilePayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 買掛金集計バッチの設定クラス
 * Spring Boot 3.x / Spring Batch 5.x対応:
 * JobBuilderFactory, StepBuilderFactory の代わりに
 * JobRepository, PlatformTransactionManager を直接利用
 *
 * <p><b>廃止予定 (Phase B' 移行)</b>:
 * 集計処理は {@link AccountsPayableAggregationConfig} に分離済み、
 * 検証処理は {@link AccountsPayableVerificationConfig} に分離済み。
 * 本クラスは {@code finance.legacy-payable-job=true} 設定時のみ Bean 登録される。
 * 本番運用 1 ヶ月後に物理削除予定 (DD-09)。
 */
@Deprecated
@Configuration
@ConditionalOnProperty(name = "finance.legacy-payable-job", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Log4j2
public class AccountsPayableSummaryConfig {

    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final AccountsPayableSummaryInitTasklet accountsPayableSummaryInitTasklet;
    @NonNull
    private final AccountsPayableSummaryTasklet accountsPayableSummaryTasklet;
    @NonNull
    private final AccountsPayableVerificationReportTasklet accountsPayableVerificationReportTasklet;
    @NonNull
    private final SmilePaymentWorkTableInitTasklet smilePaymentWorkTableInitTasklet;

    // SMILE支払情報取込用のコンポーネント
    @NonNull
    private final SmilePaymentFileReader smilePaymentFileReader;
    @NonNull
    private final SmilePaymentProcessor smilePaymentProcessor;
    @NonNull
    private final SmilePaymentWriter smilePaymentWriter;

    @Bean
    public JobExecutionListener accountsPayableSummaryListener() {
        return new JobStartEndListener();
    }

    /**
     * 買掛金集計ジョブ
     * 注意：Bean名は「ジョブ名 + Job」の形式で定義すること
     * 例：accountsPayableSummaryJob
     * これにより、実行時に指定するジョブ名「accountsPayableSummary」と連携する
     */
    @Bean
    public Job accountsPayableSummaryJob() {
        return new JobBuilder("accountsPayableSummary", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(accountsPayableSummaryListener())
                .start(accountsPayableSummaryInitStep())   // 最初に買掛金サマリーテーブルの初期化ステップを実行
                .next(smilePaymentWorkTableInitStep())     // 次にワークテーブル初期化ステップを実行
                .next(smilePaymentImportStep())
                .next(accountsPayableSummaryStep())
                .next(accountsPayableVerificationReportStep())
                .build();
    }

    /**
     * 買掛金サマリーテーブル初期化ステップ
     */
    @Bean
    public Step accountsPayableSummaryInitStep() {
        return new StepBuilder("accountsPayableSummaryInitStep", jobRepository)
                .tasklet(accountsPayableSummaryInitTasklet, transactionManager)
                .build();
    }

    /**
     * SmilePaymentワークテーブル初期化ステップ
     */
    @Bean
    public Step smilePaymentWorkTableInitStep() {
        return new StepBuilder("smilePaymentWorkTableInitStep", jobRepository)
                .tasklet(smilePaymentWorkTableInitTasklet, transactionManager)
                .build();
    }

    /**
     * SMILE支払情報取込ステップ
     */
    @Bean
    public Step smilePaymentImportStep() {
        return new StepBuilder("smilePaymentImportStep", jobRepository)
                .<SmilePaymentFile, WSmilePayment>chunk(100, transactionManager)
                .reader(smilePaymentFileReader)
                .processor(smilePaymentProcessor)
                .writer(smilePaymentWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(10000)
                .build();
    }

    /**
     * 買掛金集計ステップ
     */
    @Bean
    public Step accountsPayableSummaryStep() {
        return new StepBuilder("accountsPayableSummaryStep", jobRepository)
                .tasklet(accountsPayableSummaryTasklet, transactionManager)
                .build();
    }

    /**
     * 買掛金検証レポート出力ステップ
     * 検証チェック機能も統合されているため、このステップでチェックとレポート出力の両方を行う
     */
    @Bean
    public Step accountsPayableVerificationReportStep() {
        return new StepBuilder("accountsPayableVerificationReportStep", jobRepository)
                .tasklet(accountsPayableVerificationReportTasklet, transactionManager)
                .build();
    }
}
