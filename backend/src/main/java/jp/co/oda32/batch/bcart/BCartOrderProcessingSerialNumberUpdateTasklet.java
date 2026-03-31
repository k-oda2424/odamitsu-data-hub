package jp.co.oda32.batch.bcart;

import jp.co.oda32.domain.service.bcart.BCartLogisticsService;
import jp.co.oda32.domain.service.bcart.TSmileOrderImportFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * B-Cartの注文データをSMILEに取り込んだ後、採番されたSMILE処理連番をW_SMILE_ORDER_FILEテーブルに更新をかけるタスクレット
 *
 * @author k_oda
 * @since 2019/07/16
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class BCartOrderProcessingSerialNumberUpdateTasklet implements Tasklet {
    @Autowired
    private final TSmileOrderImportFileService tSmileOrderImportFileService;
    @Autowired
    private final BCartLogisticsService bCartLogisticsService;

    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("b-cart注文データSMILE処理連番更新バッチ起動");
        try {
            updateProcessingSerialNumber();
            updateBCartLogisticsShipmentCodes();
        } catch (Exception e) {
            // 例外をキャッチしてログに記録する
            log.error("Error occurred in BCartOrderProcessingSerialNumberUpdateTasklet", e);
            // 処理を終了させ、次のステップに進む
            return RepeatStatus.FINISHED;
        }
        log.info("b-cart注文データSMILE処理連番更新バッチ正常終了");
        return RepeatStatus.FINISHED;
    }

    /**
     * t_sile_order_import_fileの処理連番（仮）をSMILEシステムの処理連番で更新します
     */
    public void updateProcessingSerialNumber() {
        tSmileOrderImportFileService.updateProcessingSerialNumbers();
    }

    /**
     * b_cart_logisticsのshipment_codeにt_sile_order_import_fileの処理連番で更新します。
     */
    private void updateBCartLogisticsShipmentCodes() {
        bCartLogisticsService.updateShipmentCodes();
    }

}
