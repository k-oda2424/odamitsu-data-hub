package jp.co.oda32.batch.bcart.config;

import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.bcart.BCartOrderConvertSmileOrderFileTasklet;
import jp.co.oda32.batch.bcart.BCartOrderRegisterTasklet;
import jp.co.oda32.batch.bcart.SmileDestinationFileOutPutTasklet;
import jp.co.oda32.batch.bcart.SmileOrderFileOutPutTasklet;
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
public class BCartOrderImportConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BCartOrderRegisterTasklet bCartOrderRegisterTasklet;
    private final BCartOrderConvertSmileOrderFileTasklet bCartOrderConvertSmileOrderFileTasklet;
    private final SmileDestinationFileOutPutTasklet smileDestinationFileOutPutTasklet;
    private final SmileOrderFileOutPutTasklet smileOrderFileOutPutTasklet;

    @Bean
    public JobExecutionListener bCartOrderImportListener() {
        return new JobStartEndListener();
    }

    @Bean(name = "bCartOrderImportJob")
    public Job bCartOrderImportJob() {
        return new JobBuilder("bCartOrderImport", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(bCartOrderImportListener())
                .flow(bCartOrderRegisterStep())
                .next(bCartOrderConvertSmileOrderFileStep())
                .next(smileDestinationFileOutPutStep())
                .next(smileOrderFileOutPutStep())
                .end()
                .build();
    }

    @Bean
    public Step bCartOrderRegisterStep() {
        return new StepBuilder("bCartOrderRegisterStep", jobRepository)
                .tasklet(bCartOrderRegisterTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step bCartOrderConvertSmileOrderFileStep() {
        return new StepBuilder("bCartOrderConvertSmileOrderFileStep", jobRepository)
                .tasklet(bCartOrderConvertSmileOrderFileTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step smileDestinationFileOutPutStep() {
        return new StepBuilder("smileDestinationFileOutPutStep", jobRepository)
                .tasklet(smileDestinationFileOutPutTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step smileOrderFileOutPutStep() {
        return new StepBuilder("smileOrderFileOutPutStep", jobRepository)
                .tasklet(smileOrderFileOutPutTasklet, transactionManager)
                .build();
    }
}
