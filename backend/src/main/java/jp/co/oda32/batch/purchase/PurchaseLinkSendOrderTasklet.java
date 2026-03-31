package jp.co.oda32.batch.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.SendOrderDetailStatus;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.model.purchase.TSendOrderDetail;
import jp.co.oda32.domain.service.purchase.TPurchaseDetailService;
import jp.co.oda32.domain.service.purchase.TSendOrderDetailService;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 発注と仕入を紐付けるタスクレットクラス
 *
 * @author k_oda
 * @since 2019/08/17
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class PurchaseLinkSendOrderTasklet implements Tasklet {
    @NonNull
    private TPurchaseDetailService tPurchaseDetailService;

    @NonNull
    private TSendOrderDetailService tSendOrderDetailService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 仕入入力されていない発注明細を取得
        List<TSendOrderDetail> tSendOrderDetailList = this.tSendOrderDetailService.findBySendOrderDetailStatusList(
                Arrays.asList(SendOrderDetailStatus.SEND_ORDER.getCode()
                        , SendOrderDetailStatus.ARRIVAL_TO_PROMISE.getCode()
                        , SendOrderDetailStatus.ARRIVED.getCode()));
        // 1ヶ月以内の発注番号の登録のない仕入明細を取得
        List<TPurchaseDetail> tPurchaseDetailList = this.tPurchaseDetailService.findByNotInputSendOrder(LocalDateTime.now().minusMonths(1).toLocalDate());
        tSendOrderDetailList.forEach(
                tSendOrderDetail -> {
                    AtomicBoolean isSet = new AtomicBoolean(false);
                    tPurchaseDetailList.stream()
                            .filter(purchaseDetail -> purchaseDetail.getSendOrderNo() == null)
                            .filter(purchaseDetail -> purchaseDetail.getGoodsNo() != null)
                            .filter(purchaseDetail -> purchaseDetail.getGoodsNo().equals(tSendOrderDetail.getGoodsNo()))
                            .filter(purchaseDetail -> purchaseDetail.getGoodsNum().equals(tSendOrderDetail.getSendOrderNum()))
                            .filter(purchaseDetail -> purchaseDetail.getPurchaseDate().isAfter(tSendOrderDetail.getTSendOrder().getSendOrderDateTime().toLocalDate()))
                            .forEach(purchaseDetail -> {
                                purchaseDetail.setSendOrderNo(tSendOrderDetail.getSendOrderNo());
                                purchaseDetail.setSendOrderDetailNo(tSendOrderDetail.getSendOrderDetailNo());
                                log.info(String.format("仕入番号:%d 仕入明細番号:%d は発注番号:%d 発注明細番号:%dに紐付けされました。"
                                        , purchaseDetail.getPurchaseNo(), purchaseDetail.getPurchaseDetailNo()
                                        , tSendOrderDetail.getSendOrderNo(), tSendOrderDetail.getSendOrderDetailNo()));
                                if (tSendOrderDetail.getSendOrderDetailStatus().equals(SendOrderDetailStatus.ARRIVED.getCode())) {
                                    purchaseDetail.setStockProcessFlg(Flag.YES.getValue());
                                    log.info("発注明細ステータスが入荷済なので在庫処理フラグを立てました。");
                                }
                                if (tSendOrderDetail.getArrivedDate() == null) {
                                    tSendOrderDetail.setArrivedDate(purchaseDetail.getPurchaseDate());
                                }
                                if (tSendOrderDetail.getArrivedNum() == null) {
                                    tSendOrderDetail.setArrivedNum(purchaseDetail.getGoodsNum());
                                }
                                isSet.set(true);
                            });
                    if (isSet.get()) {
                        tSendOrderDetail.setSendOrderDetailStatus(SendOrderDetailStatus.PURCHASED.getCode());

                    }
                }
        );

        // マッチングした発注明細,仕入明細を更新する
        List<TSendOrderDetail> updateSendOrderDetailList = tSendOrderDetailList.stream()
                .filter(sendOrderDetail -> StringUtil.isEqual(SendOrderDetailStatus.PURCHASED.getCode(), sendOrderDetail.getSendOrderDetailStatus()))
                .collect(Collectors.toList());
        List<TPurchaseDetail> updatePurchaseDetailList = tPurchaseDetailList.stream()
                .filter(purchaseDetail -> purchaseDetail.getSendOrderNo() != null)
                .collect(Collectors.toList());
        this.tSendOrderDetailService.update(updateSendOrderDetailList);
        this.tPurchaseDetailService.update(updatePurchaseDetailList);
        return RepeatStatus.FINISHED;
    }
}
