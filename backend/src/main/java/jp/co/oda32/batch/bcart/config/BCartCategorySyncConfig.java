package jp.co.oda32.batch.bcart.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.bcart.BCartCategoriesSyncTasklet;
import jp.co.oda32.batch.bcart.BCartCategoriesUpdateTasklet;
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
public class BCartCategorySyncConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BCartCategoriesSyncTasklet bCartCategoriesSyncTasklet;
    private final BCartCategoriesUpdateTasklet bCartCategoriesUpdateTasklet;

    @Bean
    public JobExecutionListener bCartCategorySyncListener() {
        return new JobStartEndListener();
    }

    @Bean(name = "bCartCategorySyncJob")
    public Job bCartCategorySyncJob() {
        return new JobBuilder("bCartCategorySync", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(bCartCategorySyncListener())
                .flow(bCartCategoriesSyncStep())
                .end()
                .build();
    }

    @Bean(name = "bCartCategoryUpdateJob")
    public Job bCartCategoryUpdateJob() {
        return new JobBuilder("bCartCategoryUpdate", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(bCartCategorySyncListener())
                .flow(bCartCategoriesUpdateStep())
                .end()
                .build();
    }

    @Bean
    public Step bCartCategoriesSyncStep() {
        return new StepBuilder("bCartCategoriesSyncStep", jobRepository)
                .tasklet(bCartCategoriesSyncTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step bCartCategoriesUpdateStep() {
        return new StepBuilder("bCartCategoriesUpdateStep", jobRepository)
                .tasklet(bCartCategoriesUpdateTasklet, transactionManager)
                .build();
    }
}
