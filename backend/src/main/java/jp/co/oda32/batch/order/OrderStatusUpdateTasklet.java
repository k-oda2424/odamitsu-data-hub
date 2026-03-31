package jp.co.oda32.batch.order;

import jp.co.oda32.constant.*;
import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.service.order.TDeliveryDetailService;
import jp.co.oda32.domain.service.order.TDeliveryService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import jp.co.oda32.domain.service.order.TOrderService;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 注文系ステータスを更新するクラス
 *
 * @author k_oda
 * @since 2019/07/16
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class OrderStatusUpdateTasklet implements Tasklet {
    @NonNull
    private TOrderDetailService orderDetailService;
    @NonNull
    private TOrderService orderService;
    @NonNull
    private TDeliveryDetailService tDeliveryDetailService;
    @NonNull
    private TDeliveryService tDeliveryService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 在庫引当(未来日付)→出荷待ち
        // 在庫引当(現在過去日付)→出荷済
        this.updateOrder();
        // 出荷ステータス
        this.updateDelivery();
        // TODO 他にステータスを変更する場合は追加する
        return RepeatStatus.FINISHED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void updateOrder() throws Exception {
        // 注文明細ステータスが在庫引当のものを取得
        List<TOrderDetail> orderDetailList = this.orderDetailService.findByOrderDetailStatus(OrderDetailStatus.ALLOCATION);
        if (CollectionUtil.isEmpty(orderDetailList)) {
            // 対象明細なし
            return;
        }
        // 在庫引当(現在過去日付)→出荷済
        for (TOrderDetail orderDetail : orderDetailList) {
            LocalDate today = LocalDate.now();
            if (today.isAfter(orderDetail.getTDelivery().getSlipDate()) || today.isEqual(orderDetail.getTDelivery().getSlipDate())) {
                orderDetail.setOrderDetailStatus(OrderDetailStatus.DELIVERED.getCode());
                this.orderDetailService.update(orderDetail);
            }
        }
        // 注文番号のリストを取得
        List<Integer> orderNoList = orderDetailList.stream().map(TOrderDetail::getOrderNo).collect(Collectors.toList());
        // 注文番号ごとの注文明細をすべて取得
        List<TOrderDetail> allOrderDetailList = this.orderDetailService.findByOrderNoList(orderNoList);
        // 注文ごとにまとめる
        Map<Integer, List<TOrderDetail>> orderMap = allOrderDetailList.stream().collect(Collectors.groupingBy(TOrderDetail::getOrderNo, Collectors.toList()));
        // 更新用注文番号のリスト
        List<Integer> updateWaitShippingOrderNoList = new ArrayList<>();
        List<Integer> updateDeliveredOrderNoList = new ArrayList<>();
        orderMap.forEach((orderNo, detailList) -> {
            if (detailList.stream()
                    .filter(detail -> detail.getTDelivery().getSlipDate().isBefore(LocalDate.now().plusDays(1)))
                    .allMatch(detail -> StringUtil.isEqual(detail.getOrderDetailStatus(), OrderDetailStatus.ALLOCATION.getCode()))) {
                // すべてが在庫引当の場合、出荷待ち
                updateWaitShippingOrderNoList.add(orderNo);
            } else if (detailList.stream()
                    .filter(detail -> detail.getTDelivery().getSlipDate().isAfter(LocalDate.now()))
                    .allMatch(detail -> StringUtil.isEqual(detail.getOrderDetailStatus(), OrderDetailStatus.DELIVERED.getCode()))) {
                updateDeliveredOrderNoList.add(orderNo);
            }
        });
        if (!updateWaitShippingOrderNoList.isEmpty()) {
            this.orderService.updateOrderStatusByOrderNoList(orderNoList, OrderStatus.WAIT_SHIPPING);
        }
        if (!updateDeliveredOrderNoList.isEmpty()) {
            this.orderService.updateOrderStatusByOrderNoList(orderNoList, OrderStatus.DELIVERED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void updateDelivery() throws Exception {
        // 注文明細ステータスが出荷済かつ出荷明細ステータスが出荷待のものを取得
        List<TDeliveryDetail> tDeliveryDetailList = this.tDeliveryDetailService.findForUpdateDeliveryDetailStatus(DeliveryDetailStatus.WAIT_SHIPPING.getValue(), OrderDetailStatus.DELIVERED.getCode(), Flag.NO);
        List<Integer> deliveryNoList = new ArrayList<>();
        for (TDeliveryDetail tDeliveryDetail : tDeliveryDetailList) {
            tDeliveryDetail.setDeliveryDetailStatus(DeliveryDetailStatus.DELIVERED.getValue());
            this.tDeliveryDetailService.update(tDeliveryDetail);
            deliveryNoList.add(tDeliveryDetail.getDeliveryNo());
        }
        if (CollectionUtil.isEmpty(deliveryNoList)) {
            return;
        }
        deliveryNoList = deliveryNoList.stream().distinct().collect(Collectors.toList());
        // 出荷済みになった出荷明細を出荷番号ですべて取得
        List<TDeliveryDetail> allTDeliveryDetailList = this.tDeliveryDetailService.findByDeliveryNoList(deliveryNoList);
        // 出荷番号毎に出荷明細リストをまとめる
        Map<Integer, List<TDeliveryDetail>> deliveryMap = allTDeliveryDetailList.stream().collect(Collectors.groupingBy(TDeliveryDetail::getDeliveryNo, Collectors.toList()));
        // すべて出荷済みになった出荷明細の出荷番号を控える
        List<Integer> updateDeliveryNoList = new ArrayList<>();
        deliveryMap.forEach((deliveryNo, detailList) -> {
            if (detailList.stream().allMatch(detail -> detail.getDeliveryDetailStatus().equals(DeliveryDetailStatus.DELIVERED.getValue()))) {
                updateDeliveryNoList.add(deliveryNo);
            }
        });
        if (!updateDeliveryNoList.isEmpty()) {
            // 該当の出荷番号の出荷テーブルの出荷ステータスを出荷済みにする
            this.tDeliveryService.updateDeliveryStatusByDeliveryNoList(deliveryNoList, DeliveryStatus.DELIVERED);
            // 該当の出荷番号の出荷日を入れる
            this.tDeliveryService.updateDeliveryDateByDeliveryNoList(deliveryNoList);
        }
    }
}
