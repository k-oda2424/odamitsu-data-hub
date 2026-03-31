package jp.co.oda32.batch.order;

import jp.co.oda32.batch.stock.StockManager;
import jp.co.oda32.constant.OrderDetailStatus;
import jp.co.oda32.constant.StockLogReason;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.model.stock.TStock;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.DateTimeUtil;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 注文に対する在庫引き当てを実行するクラス
 *
 * @author k_oda
 * @since 2019/04/11
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class StockAllocateTasklet implements Tasklet {
    @NonNull
    private TOrderDetailService orderDetailService;
    @NonNull
    private StockManager stockManager;
    @NonNull
    private MShopService mShopService;
    // 直近3ヶ月で動いた在庫のリスト
    private List<TStock> moveStockList;
    // 在庫不足の商品リスト
    private List<Integer> shortGoodsNoList = new ArrayList<>();
    // ショップ番号と会社番号のMap(注文明細の会社番号は注文した側の会社番号)
    private Map<Integer, Integer> shopMap = new HashMap<>();

    private Map<StockListKey, List<TStock>> stockListMap = new HashMap<>();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 注文明細ステータスが受付と入荷待ちのものを取得
        List<TOrderDetail> orderDetailList = this.orderDetailService.findByOrderDetailStatusList(OrderDetailStatus.RECEIPT.getCode(), OrderDetailStatus.BACK_ORDERED.getCode());
        // 直送の注文明細は引当から除外する 日付→注文受付順
//        orderDetailList = orderDetailList.stream()
//                .filter(tOrderDetail -> Flag.YES.getValue().equals(tOrderDetail.getTDelivery().getDirectShippingFlg()))
//                .sorted(Comparator.comparing(TOrderDetail::getOrderDateTime)
//                        .thenComparing(Comparator.comparing(TOrderDetail::getOrderNo)
//                                .thenComparing(TOrderDetail::getOrderDetailNo)))
//                .collect(Collectors.toList());
        // 直送の仕入れ伝票かどうか判定できないので直送は除外しない
        log.info(String.format("対象注文明細数:%d件", orderDetailList.size()));
        // 直近3ヶ月で動いた在庫リスト
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        this.moveStockList = this.stockManager.findStockList(null, null, threeMonthsAgo);
        // 注文を受けたショップの検索
        List<Integer> shopNoList = orderDetailList.stream().map(TOrderDetail::getShopNo).distinct().collect(Collectors.toList());
        this.shopMap = this.mShopService.findByShopNoList(shopNoList).stream().collect(Collectors.toMap(MShop::getShopNo, MShop::getCompanyNo));
        // 商品ごとに分割
        Map<Integer, List<TOrderDetail>> orderDetailListGroupByGoods = orderDetailList.stream().collect(Collectors.groupingBy(TOrderDetail::getGoodsNo, Collectors.toList()));
        orderDetailListGroupByGoods.values()
                .forEach(this::allocateByGoodsNo);
        return RepeatStatus.FINISHED;
    }

    private void allocateByGoodsNo(List<TOrderDetail> orderDetailList) {
        orderDetailList.sort(Comparator.comparing(TOrderDetail::getOrderDateTime)
                .thenComparing(Comparator.comparing(TOrderDetail::getOrderNo)
                        .thenComparing(TOrderDetail::getOrderDetailNo)));
        orderDetailList.forEach(this::stockProcess);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stockProcess(TOrderDetail tOrderDetail) {
        try {
            if (this.shortGoodsNoList.contains(tOrderDetail.getGoodsNo())) {
                // 在庫不足 → 入荷待ち
                tOrderDetail.setOrderDetailStatus(OrderDetailStatus.BACK_ORDERED.getCode());
                this.orderDetailService.update(tOrderDetail);
                return;
            }
            if (tOrderDetail.getGoodsName().contains("手入力")) {
                // 手入力商品はsmileに仕入入力しないので即時引き当て済みにする
                tOrderDetail.setOrderDetailStatus(OrderDetailStatus.ALLOCATION.getCode());
                this.orderDetailService.update(tOrderDetail);
                return;
            }
            // 現在の在庫を検索
            StockListKey key = StockListKey.builder().goodsNo(tOrderDetail.getGoodsNo()).shopNo(tOrderDetail.getShopNo()).build();
            List<TStock> stockList;
            if (this.stockListMap.containsKey(key)) {
                stockList = this.stockListMap.get(key);
            } else {
                stockList = this.moveStockList.stream()
                        .filter(moveStock -> moveStock.getGoodsNo().equals(tOrderDetail.getGoodsNo()))
                        .filter(moveStock -> moveStock.getCompanyNo().equals(this.shopMap.get(tOrderDetail.getShopNo())))
                        .collect(Collectors.toList());
                if (CollectionUtil.isEmpty(stockList)) {
                    stockList = this.stockManager.findStock(tOrderDetail.getGoodsNo(), this.shopMap.get(tOrderDetail.getShopNo()));
                }
            }
            // TODO warehouseの選択方法
            // 注文数のバラの数
            BigDecimal orderNum = tOrderDetail.getOrderNum().subtract(tOrderDetail.getCancelNum()).subtract(tOrderDetail.getReturnNum());
            // 在庫のバラの数(全倉庫合計)
//            int stockNum = stockList.stream().map(tStock -> this.stockManager.calculateMinGoodsUnitNum(tStock)).reduce(0, Integer::sum);
            // 1つの倉庫で足りる倉庫の在庫
//            TStock targetStock = null;
            int targetIndex = 0;
            // 仕入がしっかりできるまでマイナス在庫を許す
            if (CollectionUtil.isEmpty(stockList)) {
                // 在庫がまだ登録されていない商品（いずれ無くなる）
                shortGoodsNoList.add(tOrderDetail.getGoodsNo());
                tOrderDetail.setOrderDetailStatus(OrderDetailStatus.BACK_ORDERED.getCode());
                log.warn(String.format("在庫が登録されていません。ショップ:%d 商品番号:%d 商品コード:%s 商品名:%s 注文日時:%s"
                        , tOrderDetail.getShopNo(), tOrderDetail.getGoodsNo(), tOrderDetail.getGoodsCode(), tOrderDetail.getGoodsName(), DateTimeUtil.localDateTimeToDateTimeStr(tOrderDetail.getOrderDateTime())));
                this.orderDetailService.update(tOrderDetail);
                return;
            }
            TStock targetStock = stockList.get(targetIndex);
//            for (int i = 0; i < stockList.size(); i++) {
//                if (this.stockManager.calculateMinGoodsUnitNum(stockList.get(i)) >= orderNum) {
//                    targetStock = stockList.get(i);
//                    targetIndex = i;
//                    break;
//                }
//            }
//            if (stockNum < orderNum || targetStock == null) {
//                // 在庫不足 → 入荷待ち
//                shortGoodsNoList.add(tOrderDetail.getGoodsNo());
//                tOrderDetail.setOrderDetailStatus(OrderDetailStatus.BACK_ORDERED.getCode());
//                log.warn(String.format("在庫が不足しています。ショップ:%d 商品番号:%d 商品コード:%s 商品名:%s 現在在庫数:%d 注文数:%d 注文日時:%s"
//                        , tOrderDetail.getShopNo(), tOrderDetail.getGoodsNo(), tOrderDetail.getGoodsCode(), tOrderDetail.getGoodsName(), stockNum, orderNum, DateTimeUtil.localDateTimeToDateTimeStr(tOrderDetail.getOrderDateTime())));
//                this.orderDetailService.update(tOrderDetail);
//                return;
//            }
            // 引当
            TStock updateStock = this.stockManager.move(targetStock.getCompanyNo(), tOrderDetail.getGoodsNo(), targetStock.getWarehouseNo(), tOrderDetail.getOrderDateTime(), StockLogReason.ALLOCATE, new BigDecimal(-1).multiply(orderNum), tOrderDetail.getDeliveryNo(), null, null, false);
            if (updateStock == null) {
                this.stockListMap.put(key, stockList);
                return;
            }
            stockList.set(targetIndex, updateStock);
            tOrderDetail.setOrderDetailStatus(OrderDetailStatus.ALLOCATION.getCode());
            this.orderDetailService.update(tOrderDetail);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(String.format("在庫引当処理に失敗しました。orderNo:%d order_detail_no:%d goods_name:%s goods_num:%d error:%s"
                    , tOrderDetail.getOrderNo()
                    , tOrderDetail.getOrderDetailNo()
                    , tOrderDetail.getGoodsName()
                    , tOrderDetail.getGoodsNum()
                    , e.getMessage()));
        }
    }

}
