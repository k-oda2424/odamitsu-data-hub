package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.order.TDelivery;
import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.domain.model.order.TOrder;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.repository.order.DeletedOrderRepository;
import jp.co.oda32.domain.service.order.TDeliveryDetailService;
import jp.co.oda32.domain.service.order.TDeliveryService;
import jp.co.oda32.util.DeliveryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SMILEで削除された注文処理プロセッサー
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmileOrderDeletionProcessor extends AbstractSmileOrderImportService {

    private final DeletedOrderRepository deletedOrderRepository;
    private final TDeliveryDetailService deliveryDetailService;
    private final TDeliveryService deliveryService;
    @Autowired
    DeliveryUtil deliveryUtil;

    public List<TDeliveryDetail> findDeletTDeliveryList() {
        return this.tDeliveryDetailService.findDeletTDeliveryList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteOrder(List<TDeliveryDetail> deleteTDeliveryDetailList) throws Exception {
        List<Integer> orderNoList = new ArrayList<>();
        List<Integer> deliveryNoList = deleteTDeliveryDetailList.stream().map(TDeliveryDetail::getDeliveryNo).collect(Collectors.toList());
        for (TDeliveryDetail tDeliveryDetail : deleteTDeliveryDetailList) {
            log.info(String.format("明細削除 処理連番:%d 行番号:%d", tDeliveryDetail.getProcessingSerialNumber(), tDeliveryDetail.getDeliveryDetailNo()));
            // 削除前にt_order_detaiも特定しておく
            TOrderDetail deleteOrderDetail = tDeliveryDetail.getTOrderDetail();
            if (!orderNoList.contains(deleteOrderDetail.getOrderNo())) {
                orderNoList.add(deleteOrderDetail.getOrderNo());
            }
            tDeliveryDetailService.deletePermanently(tDeliveryDetail);
            this.tOrderDetailService.deletePermanently(deleteOrderDetail);
        }
        // t_order再計算
        List<TOrder> tOrderList = this.tOrderService.findByOrderNoList(orderNoList);
        for (TOrder tOrder : tOrderList) {
            calculateTotalAmount(tOrder);
            tOrderService.update(tOrder);
        }
        // t_delivery再計算
        List<TDelivery> tDeliveryList = this.tDeliveryService.findByDeliveryNoList(deliveryNoList);
        for (TDelivery existDelivery : tDeliveryList) {
            existDelivery = this.deliveryUtil.recalculateTDelivery(existDelivery, existDelivery.getDeliveryDetailList());
            this.tDeliveryService.update(existDelivery);
        }
    }

    /**
     * SMILEで削除された注文を検出し削除します
     *
     * @param shopNo ショップ番号
     * @return 削除された注文数
     */
    @Transactional
    public int detectAndDeleteOrders(Integer shopNo) throws Exception {
        log.info("SMILE削除注文検出処理開始 shopNo: {}", shopNo);

        try {
            // 1. SMILEで削除された出荷を検出
            List<TDelivery> deletedDeliveries = deletedOrderRepository.findDeletedDeliveries(shopNo);

            if (deletedDeliveries.isEmpty()) {
                log.info("SMILE削除注文検出処理: 削除された出荷はありません shopNo: {}", shopNo);
                return 0;
            }

            log.info("SMILE削除注文検出処理: 削除された出荷を検出しました 件数: {} shopNo: {}",
                    deletedDeliveries.size(), shopNo);

            // 2. 関連する注文を取得
            List<Integer> deliveryNos = deletedDeliveries.stream()
                    .map(TDelivery::getDeliveryNo)
                    .collect(Collectors.toList());

            List<TOrder> ordersToDelete = findOrdersByDeliveries(deliveryNos);

            if (ordersToDelete.isEmpty()) {
                log.info("SMILE削除注文検出処理: 削除された出荷に関連する注文はありません shopNo: {}", shopNo);
                return 0;
            }

            log.info("SMILE削除注文検出処理: 削除対象注文を検出しました 件数: {} shopNo: {}",
                    ordersToDelete.size(), shopNo);

            // 3. 削除処理
            int deletedCount = deleteOrders(ordersToDelete);

            // 4. 出荷データも削除
            for (TDelivery delivery : deletedDeliveries) {
                deliveryService.deletePermanently(delivery);
                log.info("SMILE削除注文検出処理: 出荷を削除しました deliveryNo: {}", delivery.getDeliveryNo());
            }

            log.info("SMILE削除注文検出処理終了 削除注文数: {} shopNo: {}", deletedCount, shopNo);
            return deletedCount;

        } catch (Exception e) {
            log.error("SMILE削除注文検出処理でエラーが発生しました shopNo: {}", shopNo, e);
            throw e;
        }
    }

    /**
     * 出荷に関連する注文を検索します
     *
     * @param deliveryNos 出荷番号リスト
     * @return 関連する注文リスト
     */
    private List<TOrder> findOrdersByDeliveries(List<Integer> deliveryNos) {
        // 出荷明細から関連する注文を検索
        List<TOrderDetail> orderDetails = tOrderDetailService.findByDeliveryNos(deliveryNos);

        List<Integer> orderNos = orderDetails.stream()
                .map(TOrderDetail::getOrderNo)
                .distinct()
                .collect(Collectors.toList());

        if (orderNos.isEmpty()) {
            return new ArrayList<>();
        }

        return tOrderService.findByOrderNoIn(orderNos);
    }

    /**
     * 注文とその関連データを削除します
     *
     * @param orders 削除対象の注文リスト
     * @return 削除した注文数
     */
    private int deleteOrders(List<TOrder> orders) {
        Date now = new Date();
        int count = 0;

        for (TOrder order : orders) {
            try {
                // 1. 注文明細を物理削除
                List<TOrderDetail> details = tOrderDetailService.findByOrderNo(order.getOrderNo());
                for (TOrderDetail detail : details) {
                    // 出荷明細を物理削除
                    if (detail.getDeliveryNo() != null) {
                        // サービスクラスから出荷明細を取得
                        List<TDeliveryDetail> deliveryDetails =
                                deliveryDetailService.findByDeliveryNoAndOrderDetailNo(
                                        detail.getDeliveryNo(), detail.getOrderDetailNo());

                        for (TDeliveryDetail deliveryDetail : deliveryDetails) {
                            deliveryDetailService.deletePermanently(deliveryDetail);
                        }
                    }
                    // 注文明細を物理削除
                    tOrderDetailService.deletePermanently(detail);
                }

                // 2. 注文を物理削除
                tOrderService.deletePermanently(order);

                count++;
                log.info("SMILE削除注文検出処理: 注文を削除しました orderNo: {}", order.getOrderNo());

            } catch (Exception e) {
                log.error("SMILE削除注文検出処理: 注文削除中にエラーが発生しました orderNo: {}",
                        order.getOrderNo(), e);
                // 例外をスローせず、次の注文の処理を続行
            }
        }

        return count;
    }
}