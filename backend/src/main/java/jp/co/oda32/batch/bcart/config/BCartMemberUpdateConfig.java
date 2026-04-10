package jp.co.oda32.batch.bcart.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.bcart.BCartMemberDeliveryImportTasklet;
import jp.co.oda32.batch.bcart.BCartMemberImportTasklet;
import jp.co.oda32.batch.bcart.RegisterBCartMemberTasklet;
import jp.co.oda32.batch.bcart.SmilePartnerFileOutPutTasklet;
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
public class BCartMemberUpdateConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BCartMemberImportTasklet bCartMemberImportTasklet;
    private final BCartMemberDeliveryImportTasklet bCartMemberDeliveryImportTasklet;
    private final SmilePartnerFileOutPutTasklet smilePartnerFileOutPutTasklet;
    private final RegisterBCartMemberTasklet registerBCartMemberTasklet;

    @Bean
    public JobExecutionListener bCartMemberUpdateListener() {
        return new JobStartEndListener();
    }

    @Bean(name = "bCartMemberUpdateJob")
    public Job bCartMemberUpdateJob() {
        return new JobBuilder("bCartMemberUpdate", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(bCartMemberUpdateListener())
                .flow(bCartMemberImportStep())
                .next(bCartMemberDeliveryImportStep())
                .next(smilePartnerFileOutPutStep())
                .next(registerBCartMemberStep())
                .end()
                .build();
    }

    @Bean
    public Step bCartMemberImportStep() {
        return new StepBuilder("bCartMemberImportStep", jobRepository)
                .tasklet(bCartMemberImportTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step bCartMemberDeliveryImportStep() {
        return new StepBuilder("bCartMemberDeliveryImportStep", jobRepository)
                .tasklet(bCartMemberDeliveryImportTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step smilePartnerFileOutPutStep() {
        return new StepBuilder("smilePartnerFileOutPutStep", jobRepository)
                .tasklet(smilePartnerFileOutPutTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step registerBCartMemberStep() {
        return new StepBuilder("registerBCartMemberStep", jobRepository)
                .tasklet(registerBCartMemberTasklet, transactionManager)
                .build();
    }
}
