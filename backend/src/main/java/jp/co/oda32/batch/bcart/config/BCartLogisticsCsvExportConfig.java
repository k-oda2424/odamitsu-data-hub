package jp.co.oda32.batch.bcart.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.bcart.BCartLogisticsCsvOutputTasklet;
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
public class BCartLogisticsCsvExportConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BCartLogisticsCsvOutputTasklet bCartLogisticsCsvOutputTasklet;

    @Bean
    public JobExecutionListener bCartLogisticsCsvExportListener() {
        return new JobStartEndListener();
    }

    @Bean(name = "bCartLogisticsCsvExportJob")
    public Job bCartLogisticsCsvExportJob() {
        return new JobBuilder("bCartLogisticsCsvExport", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(bCartLogisticsCsvExportListener())
                .flow(bCartLogisticsCsvOutputStep())
                .end()
                .build();
    }

    @Bean
    public Step bCartLogisticsCsvOutputStep() {
        return new StepBuilder("bCartLogisticsCsvOutputStep", jobRepository)
                .tasklet(bCartLogisticsCsvOutputTasklet, transactionManager)
                .build();
    }
}
