package jp.co.oda32.batch.smile.config;

import jp.co.oda32.batch.ExitStatusChangeListener;
import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.order.OrderNumCountTasklet;
import jp.co.oda32.batch.order.OrderStatusUpdateTasklet;
import jp.co.oda32.batch.order.StockAllocateTasklet;
import jp.co.oda32.batch.order.VSalesMonthlySummaryRefreshTasklet;
import jp.co.oda32.batch.smile.SmileOrderFile;
import jp.co.oda32.batch.smile.SmileOrderFileProcessor;
import jp.co.oda32.batch.smile.SmileOrderFileReader;
import jp.co.oda32.batch.smile.SmileOrderFileWriter;
import jp.co.oda32.batch.stock.ShopAppropriateStockCalculateTasklet;
import jp.co.oda32.batch.util.FileManagerTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Smile注文取り込みバッチの設定クラス
 * Spring Batch 5.x対応: JobBuilderFactory, StepBuilderFactory を廃止し
 * JobRepository, PlatformTransactionManager を直接利用する形に変更
 *
 * @author k_oda
 * @since 2018/06/29
 */
@Configuration
@RequiredArgsConstructor
public class SmileOrderFileImportConfig {
    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final SmileOrderFileReader smileOrderFileReader;
    @NonNull
    private final SmileOrderFileProcessor smileOrderFileProcessor;
    @NonNull
    private final SmileOrderFileWriter smileOrderFileWriter;
    @NonNull
    private final OrderNumCountTasklet orderNumCountTasklet;
    @NonNull
    private final StockAllocateTasklet stockAllocateTasklet;
    @NonNull
    private final VSalesMonthlySummaryRefreshTasklet vSalesMonthlySummaryRefreshTasklet;
    @NonNull
    private final OrderStatusUpdateTasklet orderStatusUpdateTasklet;
    @NonNull
    private final ShopAppropriateStockCalculateTasklet shopAppropriateStockCalculateTasklet;
    @NonNull
    private final FileManagerTasklet fileManagerTasklet;
    @Value("input/smile_order_import.csv")
    private Resource inputResources;

    @Bean
    public JobExecutionListener smileOrderJobListener() {
        return new JobStartEndListener();
    }

    /**
     * Smile注文取込バッチのジョブ定義
     * 注意：Bean名は「ジョブ名 + Job」の形式で定義すること
     * 例：smileOrderFileImportJob
     * これにより、実行時に指定するジョブ名「smileOrderFileImport」と連携する
     */
    @Bean
    public Job smileOrderFileImportJob() {
        return new JobBuilder("smileOrderFileImport", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(smileOrderJobListener())
                .flow(smileOrderFileImportStep())
                // stockAllocateStep: t_stockベースの在庫引当は使用しない方針のため除外
                .next(orderStatusUpdateStep())
                .next(orderNumCountStep())
                .next(shopAppropriateStockCalculateStep())
                .next(vSalesMonthlySummaryRefreshStep())
                .next(fileMoveStep())
                .end()
                .build();
    }

    public Step smileOrderFileImportStep() {
        return new StepBuilder("smileOrderFileImportStep", jobRepository)
                .<SmileOrderFile, SmileOrderFile>chunk(500, transactionManager)
                .reader(this.smileOrderFileReader)
                .processor(this.smileOrderFileProcessor)
                .writer(this.smileOrderFileWriter)
                .listener(new ExitStatusChangeListener())
                .build();
    }

    public Step stockAllocateStep() {
        return new StepBuilder("stockAllocateStep", jobRepository)
                .tasklet(stockAllocateTasklet, transactionManager)
                .build();
    }

    public Step orderStatusUpdateStep() {
        return new StepBuilder("orderStatusUpdateStep", jobRepository)
                .tasklet(orderStatusUpdateTasklet, transactionManager)
                .build();
    }

    public Step shopAppropriateStockCalculateStep() {
        return new StepBuilder("shopAppropriateStockCalculateStep", jobRepository)
                .tasklet(shopAppropriateStockCalculateTasklet, transactionManager)
                .build();
    }

    public Step vSalesMonthlySummaryRefreshStep() {
        return new StepBuilder("vSalesMonthlySummaryRefreshStep", jobRepository)
                .tasklet(vSalesMonthlySummaryRefreshTasklet, transactionManager)
                .build();
    }

    public Step fileMoveStep() {
        this.fileManagerTasklet.setResources(inputResources);
        return new StepBuilder("fileMoveStep", jobRepository)
                .tasklet(fileManagerTasklet, transactionManager)
                .build();
    }

    public Step orderNumCountStep() {
        return new StepBuilder("orderNumCountStep", jobRepository)
                .tasklet(orderNumCountTasklet, transactionManager)
                .build();
    }
}
