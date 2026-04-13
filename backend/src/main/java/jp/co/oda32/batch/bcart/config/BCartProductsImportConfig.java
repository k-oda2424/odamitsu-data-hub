package jp.co.oda32.batch.bcart.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.bcart.BCartProductSetsImportTasklet;
import jp.co.oda32.batch.bcart.BCartProductsImportTasklet;
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

@Configuration
@RequiredArgsConstructor
public class BCartProductsImportConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BCartProductsImportTasklet bCartProductsImportTasklet;
    private final BCartProductSetsImportTasklet bCartProductSetsImportTasklet;

    @Bean
    public JobExecutionListener bCartProductsImportListener() {
        return new JobStartEndListener();
    }

    @Bean(name = "bCartProductsImportJob")
    public Job bCartProductsImportJob() {
        return new JobBuilder("bCartProductsImport", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(bCartProductsImportListener())
                .flow(bCartProductsImportStep())
                .next(bCartProductSetsImportStep())
                .end()
                .build();
    }

    @Bean
    public Step bCartProductsImportStep() {
        return new StepBuilder("bCartProductsImportStep", jobRepository)
                .tasklet(bCartProductsImportTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step bCartProductSetsImportStep() {
        return new StepBuilder("bCartProductSetsImportStep", jobRepository)
                .tasklet(bCartProductSetsImportTasklet, transactionManager)
                .build();
    }
}
