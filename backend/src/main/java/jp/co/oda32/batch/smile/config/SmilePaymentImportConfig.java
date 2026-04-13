package jp.co.oda32.batch.smile.config;

import jp.co.oda32.batch.ExitStatusChangeListener;
import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.smile.SmilePaymentFile;
import jp.co.oda32.batch.smile.SmilePaymentFileReader;
import jp.co.oda32.batch.smile.SmilePaymentProcessor;
import jp.co.oda32.batch.smile.SmilePaymentWorkTableInitTasklet;
import jp.co.oda32.batch.smile.SmilePaymentWriter;
import jp.co.oda32.batch.util.FileManagerTasklet;
import jp.co.oda32.domain.model.smile.WSmilePayment;
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
 * SMILE支払情報取込バッチの設定クラス
 * フロー: ワークテーブル初期化 → CSV取込（w_smile_payment → t_smile_payment同期） → ファイル移動
 */
@Configuration
@RequiredArgsConstructor
public class SmilePaymentImportConfig {
    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final SmilePaymentWorkTableInitTasklet smilePaymentWorkTableInitTasklet;
    @NonNull
    private final SmilePaymentFileReader smilePaymentFileReader;
    @NonNull
    private final SmilePaymentProcessor smilePaymentProcessor;
    @NonNull
    private final SmilePaymentWriter smilePaymentWriter;
    @NonNull
    private final FileManagerTasklet fileManagerTasklet;
    @Value("input/smile_payment_import.csv")
    private Resource inputResources;

    @Bean
    public JobExecutionListener smilePaymentJobListener() {
        return new JobStartEndListener();
    }

    @Bean
    public Job smilePaymentImportJob() {
        return new JobBuilder("smilePaymentImport", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(smilePaymentJobListener())
                .flow(smilePaymentWorkTableInitStep())
                .next(smilePaymentImportStep())
                .next(smilePaymentFileMoveStep())
                .end()
                .build();
    }

    private Step smilePaymentWorkTableInitStep() {
        return new StepBuilder("smilePaymentWorkTableInitStep", jobRepository)
                .tasklet(smilePaymentWorkTableInitTasklet, transactionManager)
                .build();
    }

    private Step smilePaymentImportStep() {
        return new StepBuilder("smilePaymentImportStep", jobRepository)
                .<SmilePaymentFile, WSmilePayment>chunk(100, transactionManager)
                .reader(smilePaymentFileReader)
                .processor(smilePaymentProcessor)
                .writer(smilePaymentWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(10000)
                .listener(new ExitStatusChangeListener())
                .build();
    }

    private Step smilePaymentFileMoveStep() {
        fileManagerTasklet.setResources(inputResources);
        return new StepBuilder("smilePaymentFileMoveStep", jobRepository)
                .tasklet(fileManagerTasklet, transactionManager)
                .build();
    }
}
