package jp.co.oda32.batch.estimate.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.estimate.*;
import lombok.RequiredArgsConstructor;
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
 * 得意先価格変更予定作成 + 見積自動生成バッチの設定クラス
 *
 * フロー:
 * Step 1: 仕入価格変更予定 → 得意先商品価格変更予定の作成（同じ掛け率で自動計算）
 * Step 2: 子得意先の変更予定 → 親得意先の変更予定の自動生成
 * Step 3: 価格変更予定 → 見積自動生成
 * Step 4: 親見積作成済みの子見積ステータスを「他同グループ提出済(40)」に更新
 * Step 5: 価格変更を得意先商品マスタへ反映
 */
@Configuration
@RequiredArgsConstructor
public class PartnerPriceChangePlanCreateConfig {
    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final PartnerPriceChangePlanCreateTasklet partnerPriceChangePlanCreateTasklet;
    @NonNull
    private final ParentPartnerPriceChangePlanCreateTasklet parentPartnerPriceChangePlanCreateTasklet;
    @NonNull
    private final PriceChangeToEstimateCreateTasklet priceChangeToEstimateCreateTasklet;
    @NonNull
    private final ParentEstimateCreatedTasklet parentEstimateCreatedTasklet;
    @NonNull
    private final PartnerPriceChangeReflectTasklet partnerPriceChangeReflectTasklet;

    @Bean
    public JobExecutionListener partnerPriceChangePlanJobListener() {
        return new JobStartEndListener();
    }

    /**
     * 得意先価格変更予定作成バッチのジョブ定義
     * Bean名 = ジョブ名 + "Job"
     */
    @Bean
    public Job partnerPriceChangePlanCreateJob() {
        return new JobBuilder("partnerPriceChangePlanCreate", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(partnerPriceChangePlanJobListener())
                .flow(partnerPriceChangePlanCreateStep())
                .next(parentPartnerPriceChangePlanCreateStep())
                .next(priceChangeToEstimateCreateStep())
                .next(parentEstimateCreatedStep())
                .next(partnerPriceChangeReflectStep())
                .end()
                .build();
    }

    public Step partnerPriceChangePlanCreateStep() {
        return new StepBuilder("partnerPriceChangePlanCreateStep", jobRepository)
                .tasklet(partnerPriceChangePlanCreateTasklet, transactionManager)
                .build();
    }

    public Step parentPartnerPriceChangePlanCreateStep() {
        return new StepBuilder("parentPartnerPriceChangePlanCreateStep", jobRepository)
                .tasklet(parentPartnerPriceChangePlanCreateTasklet, transactionManager)
                .build();
    }

    public Step priceChangeToEstimateCreateStep() {
        return new StepBuilder("priceChangeToEstimateCreateStep", jobRepository)
                .tasklet(priceChangeToEstimateCreateTasklet, transactionManager)
                .build();
    }

    public Step parentEstimateCreatedStep() {
        return new StepBuilder("parentEstimateCreatedStep", jobRepository)
                .tasklet(parentEstimateCreatedTasklet, transactionManager)
                .build();
    }

    public Step partnerPriceChangeReflectStep() {
        return new StepBuilder("partnerPriceChangeReflectStep", jobRepository)
                .tasklet(partnerPriceChangeReflectTasklet, transactionManager)
                .build();
    }
}
