package jp.co.oda32.batch.smile.config;

import jp.co.oda32.batch.ExitStatusChangeListener;
import jp.co.oda32.batch.JobStartEndListener;
import jp.co.oda32.batch.bcart.BCartOrderProcessingSerialNumberUpdateTasklet;
import jp.co.oda32.batch.order.OrderStatusUpdateTasklet;
import jp.co.oda32.batch.order.PartnerGoodsSyncTasklet;
import jp.co.oda32.batch.order.VSalesMonthlySummaryRefreshTasklet;
import jp.co.oda32.batch.smile.SmileOrderFile;
import jp.co.oda32.batch.smile.SmileOrderFileProcessor;
import jp.co.oda32.batch.smile.SmileOrderFileReader;
import jp.co.oda32.batch.smile.SmileOrderFileWriter;
import jp.co.oda32.batch.smile.SmileOrderImportTasklet;
import jp.co.oda32.batch.smile.WSmileOrderOutputFileTrancateTasklet;
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
    private final PartnerGoodsSyncTasklet partnerGoodsSyncTasklet;
    @NonNull
    private final VSalesMonthlySummaryRefreshTasklet vSalesMonthlySummaryRefreshTasklet;
    @NonNull
    private final OrderStatusUpdateTasklet orderStatusUpdateTasklet;
    @NonNull
    private final ShopAppropriateStockCalculateTasklet shopAppropriateStockCalculateTasklet;
    @NonNull
    private final FileManagerTasklet fileManagerTasklet;
    @NonNull
    private final SmileOrderImportTasklet smileOrderImportTasklet;
    @NonNull
    private final BCartOrderProcessingSerialNumberUpdateTasklet bCartOrderProcessingSerialNumberUpdateTasklet;
    @NonNull
    private final WSmileOrderOutputFileTrancateTasklet wSmileOrderOutputFileTrancateTasklet;
    @Value("input/smile_order_import.csv")
    private Resource inputResources;

    @Bean
    public JobExecutionListener smileOrderJobListener() {
        return new JobStartEndListener();
    }

    /**
     * Smile注文取込バッチのジョブ定義
     * フロー: w_smile TRUNCATE → SMILE取込 → ステータス更新 → 得意先商品同期 → 月次サマリ更新 → ファイル移動
     * <p>TRUNCATE を先頭に置かないと、過去の CSV に含まれていた行が w_smile_order_output_file に
     * 残り続け（save() の merge で UPSERT 挙動）、SMILE 側で既に削除した明細が「まだ存在する」扱いに
     * なって誤更新を引き起こす（処理連番 335597 / gyou=3 問題の原因）。
     */
    @Bean
    public Job smileOrderFileImportJob() {
        return new JobBuilder("smileOrderFileImport", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(smileOrderJobListener())
                .flow(wSmileOrderOutputFileTruncateStep())
                .next(smileOrderFileImportStep())
                // stockAllocateStep: t_stockベースの在庫引当は使用しない方針のため除外
                .next(smileOrderImportStep())
                .next(orderStatusUpdateStep())
                .next(bCartOrderProcessingSerialNumberUpdateStep())
                .next(partnerGoodsSyncStep())
                // shopAppropriateStockCalculateStep: 適正在庫計算は一旦除外
                .next(vSalesMonthlySummaryRefreshStep())
                .next(fileMoveStep())
                .end()
                .build();
    }

    /**
     * w_smile_order_output_file を TRUNCATE する step。Job の先頭で実行し、
     * 前回取込の残骸を確実に除去してから CSV を再ロードする。
     */
    @Bean
    public Step wSmileOrderOutputFileTruncateStep() {
        return new StepBuilder("wSmileOrderOutputFileTruncateStep", jobRepository)
                .tasklet(wSmileOrderOutputFileTrancateTasklet, transactionManager)
                .build();
    }

    /**
     * SMILE 売上明細ワークテーブル → t_delivery_detail 等への取込。
     * 既存受注の更新・削除処理もここで行う。
     */
    @Bean
    public Step smileOrderImportStep() {
        return new StepBuilder("smileOrderImportStep", jobRepository)
                .tasklet(smileOrderImportTasklet, transactionManager)
                .build();
    }

    /**
     * B-Cart 出荷情報入力画面の smile連番 表示に必須。
     * t_smile_order_import_file.processing_serial_number を SMILE 真正連番に更新し、
     * psn_updated=true に反映する。
     */
    @Bean
    public Step bCartOrderProcessingSerialNumberUpdateStep() {
        return new StepBuilder("bCartOrderProcessingSerialNumberUpdateStep", jobRepository)
                .tasklet(bCartOrderProcessingSerialNumberUpdateTasklet, transactionManager)
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

    public Step orderStatusUpdateStep() {
        return new StepBuilder("orderStatusUpdateStep", jobRepository)
                .tasklet(orderStatusUpdateTasklet, transactionManager)
                .build();
    }

    public Step partnerGoodsSyncStep() {
        return new StepBuilder("partnerGoodsSyncStep", jobRepository)
                .tasklet(partnerGoodsSyncTasklet, transactionManager)
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
}
