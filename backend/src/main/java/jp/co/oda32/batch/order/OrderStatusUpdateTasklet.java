package jp.co.oda32.batch.order;

import jp.co.oda32.domain.service.order.TDeliveryDetailService;
import jp.co.oda32.domain.service.order.TDeliveryService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import jp.co.oda32.domain.service.order.TOrderService;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注文系ステータスを更新するクラス。
 * <p>伝票日付(slip_date)が過去または当日の order_detail / delivery_detail を納品済に遷移させ、
 * 親 t_order / t_delivery のステータスも集計する。本システムでは在庫引当（StockAllocate）は
 * 使用しないため、伝票日付のみで判定する。
 *
 * <p>初回 catch-up 時の 528k 件規模でも native bulk UPDATE で処理するため、メモリ非消費・数秒で完走。
 *
 * <p><b>トランザクション設計（意図的な partial commit）:</b>
 * {@link #updateOrder()} と {@link #updateDelivery()} は独立した {@code REQUIRES_NEW}
 * で実行する。これは「注文側の納品済遷移は完了したが、出荷側の bulk UPDATE が一時的に
 * 失敗した」場合でも注文側の成果を保持し、リトライ時に残作業だけを再処理できるようにする
 * 設計判断。両者を同一トランザクションにまとめると、出荷側失敗時に注文側の
 * 528k 件分がまとめてロールバックされ、夜間バッチ再実行で再度 528k 件を処理する
 * 必要が生じるため採用していない。
 *
 * @author k_oda
 * @since 2019/07/16
 */
@Component
@Log4j2
public class OrderStatusUpdateTasklet implements Tasklet {

    private final TOrderDetailService orderDetailService;
    private final TOrderService orderService;
    private final TDeliveryDetailService tDeliveryDetailService;
    private final TDeliveryService tDeliveryService;

    /**
     * 自身を注入することで、同一クラス内から呼び出しても Spring AOP プロキシを経由し
     * {@code @Transactional(REQUIRES_NEW)} が有効に動作する。
     * {@code @Lazy} は循環依存回避のため。
     */
    @Autowired
    @Lazy
    private OrderStatusUpdateTasklet self;

    public OrderStatusUpdateTasklet(
            TOrderDetailService orderDetailService,
            TOrderService orderService,
            TDeliveryDetailService tDeliveryDetailService,
            TDeliveryService tDeliveryService) {
        this.orderDetailService = orderDetailService;
        this.orderService = orderService;
        this.tDeliveryDetailService = tDeliveryDetailService;
        this.tDeliveryService = tDeliveryService;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // self 経由で呼び出さないと @Transactional(REQUIRES_NEW) が効かない。
        self.updateOrder();
        self.updateDelivery();
        return RepeatStatus.FINISHED;
    }

    /**
     * 注文明細・注文のステータス更新。
     * <ol>
     *   <li>受付/入荷待ち/在庫引当 の order_detail のうち、伝票日付が過去または当日の行を '20'(納品済) に一括遷移</li>
     *   <li>配下 order_detail が全て '20' の注文 → t_order.order_status='20' に一括集計</li>
     *   <li>配下 order_detail が '20' と未納品で混在する注文 → t_order.order_status='10'(出荷待ち) に一括集計</li>
     * </ol>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrder() {
        int detailUpdated = this.orderDetailService.bulkUpdatePastDetailsToDelivered();
        log.info("注文明細を納品済に更新しました: {} 件", detailUpdated);
        if (detailUpdated == 0) {
            return;
        }
        int parentDelivered = this.orderService.bulkUpdateParentOrderToDelivered();
        log.info("注文を納品済に更新しました: {} 件", parentDelivered);
        int parentWaiting = this.orderService.bulkUpdateParentOrderToWaitShipping();
        log.info("注文を出荷待ちに更新しました: {} 件", parentWaiting);
    }

    /**
     * 出荷明細・出荷のステータス更新。
     * <ol>
     *   <li>order_detail が '20' かつ delivery_detail が '10'(出荷待ち) → '20' に一括遷移</li>
     *   <li>配下 delivery_detail が全て '20' の delivery → t_delivery.delivery_status='20' に集計</li>
     *   <li>delivery_status='20' で delivery_date が未設定の delivery → delivery_plan_date をセット</li>
     * </ol>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateDelivery() {
        int detailUpdated = this.tDeliveryDetailService.bulkUpdateDeliveryDetailsToDelivered();
        log.info("出荷明細を納品済に更新しました: {} 件", detailUpdated);
        if (detailUpdated == 0) {
            return;
        }
        int parentDelivered = this.tDeliveryService.bulkUpdateParentDeliveryToDelivered();
        log.info("出荷を納品済に更新しました: {} 件", parentDelivered);
        int dateSet = this.tDeliveryService.bulkUpdateDeliveryDateForDelivered();
        log.info("納品日を設定しました: {} 件", dateSet);
    }
}
