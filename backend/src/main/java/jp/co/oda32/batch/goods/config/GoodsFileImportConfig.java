package jp.co.oda32.batch.goods.config;

import jp.co.oda32.batch.ExitStatusChangeListener;
import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.goods.GoodsFile;
import jp.co.oda32.batch.goods.GoodsFileProcessor;
import jp.co.oda32.batch.goods.GoodsFileReader;
import jp.co.oda32.batch.goods.GoodsFileWriter;
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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 商品ファイル取込バッチ設定
 * Bean名規則: ジョブ名 + "Job" (例: goodsFileImportJob)
 */
@Configuration
@RequiredArgsConstructor
public class GoodsFileImportConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final GoodsFileReader goodsFileReader;
    private final GoodsFileProcessor goodsFileProcessor;
    private final GoodsFileWriter goodsFileWriter;

    @Bean
    public JobExecutionListener goodsFileImportListener() {
        return new JobStartEndListener();
    }

    @Bean(name = "goodsFileImportJob")
    public Job goodsFileImportJob() {
        return new JobBuilder("goodsFileImport", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(goodsFileImportListener())
                .flow(goodsFileImportStep())
                .end()
                .build();
    }

    @Bean
    public Step goodsFileImportStep() {
        return new StepBuilder("goodsFileImportStep", jobRepository)
                .<GoodsFile, GoodsFile>chunk(10, transactionManager)
                .reader(this.goodsFileReader)
                .processor(this.goodsFileProcessor)
                .writer(this.goodsFileWriter)
                .listener(new ExitStatusChangeListener())
                .build();
    }
}
