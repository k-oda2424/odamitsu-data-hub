package jp.co.oda32.batch.stock;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.StockLogReason;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.service.purchase.TPurchaseDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 仕入在庫の増減処理を実行するタスクレットクラス
 *
 * @author k_oda
 * @since 2019/07/09
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class PurchaseStockTasklet implements Tasklet {
    @NonNull
    private TPurchaseDetailService tPurchaseDetailService;

    @NonNull
    private StockManager stockManager;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 在庫処理していない仕入明細を取得
        List<TPurchaseDetail> tPurchaseDetailList = this.tPurchaseDetailService.findByStockProcessFlg(Flag.NO);
        // 得意先毎に並列処理
        tPurchaseDetailList.forEach(this::stockProcess);
        return RepeatStatus.FINISHED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void stockProcess(TPurchaseDetail purchaseDetail) {
        try {
            this.stockManager.move(purchaseDetail.getCompanyNo(), purchaseDetail.getGoodsNo(), purchaseDetail.getWarehouseNo(), purchaseDetail.getPurchaseDate().atStartOfDay(), StockLogReason.PURCHASE, purchaseDetail.getGoodsNum(), null, null, purchaseDetail.getPurchaseNo(), false);
            // 仕入れ明細の在庫処理フラグを立てる
            purchaseDetail.setStockProcessFlg(Flag.YES.getValue());
            this.tPurchaseDetailService.update(purchaseDetail);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(String.format("仕入在庫処理に失敗しました。goods_no:%d warehouse_no:%d purchase_no:%d", purchaseDetail.getGoodsNo(), purchaseDetail.getWarehouseNo(), purchaseDetail.getPurchaseNo()));
        }
    }

}
