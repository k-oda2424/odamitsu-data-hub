package jp.co.oda32.batch.purchase.config;

import jp.co.oda32.batch.ExitStatusChangeListener;
import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.purchase.*;
import jp.co.oda32.batch.smile.SmilePurchaseImportTasklet;
import jp.co.oda32.batch.smile.WSmilePurchaseOutputFileTrancateTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 仕入ファイル連携バッチの設定クラス
 * Spring Boot 3.x / Spring Batch 5.x対応版
 * JobBuilderFactory, StepBuilderFactory の代わりに
 * JobRepository, PlatformTransactionManager を直接利用
 *
 * @author k_oda
 * @since 2019/06/11
 */
@Configuration
@RequiredArgsConstructor
public class PurchaseFileImportConfig {
    @NonNull
    private final JobRepository jobRepository;
    @NonNull
    private final PlatformTransactionManager transactionManager;
    @NonNull
    private final PurchaseFileReader purchaseFileReader;
    @NonNull
    private final PurchaseFileProcessor purchaseFileProcessor;
    @NonNull
    private final PurchaseFileWriter purchaseFileWriter;
    @NonNull
    private final PurchaseLinkSendOrderTasklet purchaseLinkSendOrderTasklet;
    @NonNull
    private final PurchasePriceCreateTasklet purchasePriceCreateTasklet;
    @NonNull
    private final SmilePurchaseImportTasklet smilePurchaseImportTasklet;
    @NonNull
    protected WSmilePurchaseOutputFileTrancateTasklet wSmilePurchaseOutputFileTruncateTasklet;
    @Bean
    public JobExecutionListener purchaseJobListener() {
        return new JobStartEndListener();
    }

    /**
     * 仕入ファイル取込バッチのジョブ定義
     * 注意：Bean名は「ジョブ名 + Job」の形式で定義すること
     * 例：purchaseFileImportJob
     * これにより、実行時に指定するジョブ名「purchaseFileImport」と連携する
     */
    @Bean
    public Job purchaseFileImportJob() {
        return new JobBuilder("purchaseFileImport", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(purchaseJobListener())
                .flow(wSmilePurchaseFileTruncateStep()) // 仕入ワークテーブルのtrancate
                .next(purchaseFileImportStep()) // 仕入明細をワークテーブルに入れる
                .next(smilePurchaseImportStep()) // ワークテーブルからの仕入、仕入明細を作成
                .next(purchaseLinkSendOrderStep())
                // purchasePriceCreateStep: 仕入価格マスタの自動更新は誤更新リスクがあるため除外
                .end()
                .build();
    }

    public Step purchaseFileImportStep() {
        return new StepBuilder("purchaseFileImportStep", jobRepository)
                .<PurchaseFile, ExtPurchaseFile>chunk(500, transactionManager)
                .reader(this.purchaseFileReader)
                .processor(this.purchaseFileProcessor)
                .writer(this.purchaseFileWriter)
                .listener(new ExitStatusChangeListener())
                .build();
    }

    public Step purchaseLinkSendOrderStep() {
        return new StepBuilder("purchaseLinkSendOrderStep", jobRepository)
                .tasklet(purchaseLinkSendOrderTasklet, transactionManager)
                .build();
    }

    public Step purchasePriceCreateStep() {
        return new StepBuilder("purchasePriceCreateStep", jobRepository)
                .tasklet(purchasePriceCreateTasklet, transactionManager)
                .build();
    }

    /**
     * SMILEからの仕入明細取込ステップ
     * SMILE仕入明細ワークテーブルを使用して本システムに仕入データを登録・更新する
     */
    public Step smilePurchaseImportStep() {
        return new StepBuilder("smilePurchaseImportStep", jobRepository)
                .tasklet(smilePurchaseImportTasklet, transactionManager)
                .build();
    }

    public Step wSmilePurchaseFileTruncateStep() {
        return new StepBuilder("wSmilePurchaseFileTruncateStep", jobRepository)
                .tasklet(wSmilePurchaseOutputFileTruncateTasklet, transactionManager)
                .build();
    }
}
