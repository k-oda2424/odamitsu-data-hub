package jp.co.oda32.batch.bcart.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.bcart.BCartProductDescriptionUpdateTasklet;
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
public class BCartProductDescriptionUpdateConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BCartProductDescriptionUpdateTasklet bCartProductDescriptionUpdateTasklet;

    @Bean
    public JobExecutionListener bCartProductDescriptionUpdateListener() {
        return new JobStartEndListener();
    }

    @Bean(name = "bCartProductDescriptionUpdateJob")
    public Job bCartProductDescriptionUpdateJob() {
        return new JobBuilder("bCartProductDescriptionUpdate", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(bCartProductDescriptionUpdateListener())
                .flow(bCartProductDescriptionUpdateStep())
                .end()
                .build();
    }

    @Bean
    public Step bCartProductDescriptionUpdateStep() {
        return new StepBuilder("bCartProductDescriptionUpdateStep", jobRepository)
                .tasklet(bCartProductDescriptionUpdateTasklet, transactionManager)
                .build();
    }
}
