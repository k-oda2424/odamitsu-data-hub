package jp.co.oda32.batch.stock;

import jp.co.oda32.dto.stock.AppropriateStockEntity;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.OrderDetailStatus;
import jp.co.oda32.domain.model.embeddable.MSalesGoodsPK;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.model.stock.TShopAppropriateStock;
import jp.co.oda32.domain.service.goods.MSalesGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import jp.co.oda32.domain.service.stock.TShopAppropriateStockService;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ショップ用適正在庫を算出するタスククラス
 * 適正在庫 = リードタイム×1日平均出荷数量＋安全在庫(変動対応在庫)
 *
 * @author k_oda
 * @since 2019/05/20
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class ShopAppropriateStockCalculateTasklet extends AppropriateStockCalculateTasklet {
    @NonNull
    private TOrderDetailService orderDetailService;
    @NonNull
    private MSalesGoodsService mSalesGoodsService;
    @NonNull
    private WSalesGoodsService wSalesGoodsService;
    @NonNull
    private TShopAppropriateStockService appropriateStockService;
    private Function<TOrderDetail, AppropriateStockEntity> sumOrderDetail = (orderDetail) -> AppropriateStockEntity.builder()
            .goodsNum(orderDetail.getOrderNum().subtract(orderDetail.getCancelNum()).subtract(orderDetail.getReturnNum()))
            .goodsNo(orderDetail.getGoodsNo())
            .moveDate(orderDetail.getOrderDate())
            .leadTime(this.getLeadTime(orderDetail.getGoodsNo(), orderDetail.getShopNo()))
            .shopNo(orderDetail.getShopNo())
            .build();

    private BinaryOperator<AppropriateStockEntity> op = (a1, a2) -> {
        a1.setGoodsNum(a1.getGoodsNum().add(a2.getGoodsNum()));
        return a1;
    };

    @Override
    public int getLeadTime(int goodsNo, int shopNo) {
        MSalesGoodsPK key = MSalesGoodsPK.builder().goodsNo(goodsNo).shopNo(shopNo).build();
        if (this.leadTimeMap.containsKey(key)) {
            return Optional.ofNullable(this.leadTimeMap.get(key)).orElse(7);
        }
        int leadTime = 7;
        // 販売商品(販売商品work)から検索
        MSalesGoods mSalesGoods = this.mSalesGoodsService.getByPK(shopNo, goodsNo);
        if (mSalesGoods != null && mSalesGoods.getLeadTime() != null) {
            this.leadTimeMap.put(key, mSalesGoods.getLeadTime());
            return mSalesGoods.getLeadTime();
        }
        WSalesGoods wSalesGoods = this.wSalesGoodsService.getByPK(shopNo, goodsNo);
        if (wSalesGoods != null && wSalesGoods.getLeadTime() != null) {
            this.leadTimeMap.put(key, wSalesGoods.getLeadTime());
            return wSalesGoods.getLeadTime();
        }
        // 見つからない場合は初期値7(1週間)とする
        return leadTime;
    }

    private void setLeadTimeMap(List<Integer> goodsNoList) {
        List<MSalesGoods> mSalesGoodsList = this.mSalesGoodsService.findByGoodsNoList(goodsNoList);
        mSalesGoodsList.forEach(mSalesGoods -> {
            Integer leadTime = mSalesGoods.getLeadTime();
            this.leadTimeMap.put(MSalesGoodsPK.builder().goodsNo(mSalesGoods.getGoodsNo()).shopNo(mSalesGoods.getShopNo()).build(), leadTime);
        });
        List<WSalesGoods> wSalesGoodsList = this.wSalesGoodsService.findByGoodsNoList(goodsNoList);
        wSalesGoodsList.forEach(wSalesGoods -> {
            MSalesGoodsPK pk = MSalesGoodsPK.builder().goodsNo(wSalesGoods.getGoodsNo()).shopNo(wSalesGoods.getShopNo()).build();
            Integer leadTime = wSalesGoods.getLeadTime();
            if (!this.leadTimeMap.containsKey(pk)) {
                this.leadTimeMap.put(pk, leadTime);
            }
        });
    }

    @Override
    public Map<Integer, Map<LocalDate, Optional<AppropriateStockEntity>>> getTargetMap() {
        // 適正在庫対象商品の抽出 注文日が(引数の数字)ヶ月以内
        LocalDateTime orderDateFrom = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusMonths(this.spanMonths);
        List<TOrderDetail> orderDetailList = this.orderDetailService.find(null
                , null
                , null
                , null
                , null
                , new String[]{OrderDetailStatus.ALLOCATION.getCode(), OrderDetailStatus.DELIVERED.getCode()}
                , null
                , null
                , null
                , orderDateFrom
                , LocalDateTime.now()
                , null
                , null
                , Flag.NO);
        List<TOrderDetail> targetList = orderDetailList.stream()
                .filter(orderDetail -> !StringUtil.isOnFlg(orderDetail.getTDelivery().getDirectShippingFlg()))
                .collect(Collectors.toList());
        // 商品リードタイムをまとめて検索する
        List<Integer> goodsNoList = targetList.stream()
                .map(TOrderDetail::getGoodsNo)
                .collect(Collectors.toList());
        if (goodsNoList.size() > 1000) {
            // in句の制約があるため1000ずつに分ける
            for (int i = 0; i < goodsNoList.size() / 1000; i++) {
                this.setLeadTimeMap(goodsNoList.subList(i, i * 1000));
            }
        } else {
            this.setLeadTimeMap(goodsNoList);
        }
        // 商品毎,日付毎にまとめる
        return targetList.stream()
                .map(sumOrderDetail)
                .collect(Collectors.groupingBy(AppropriateStockEntity::getGoodsNo, Collectors.groupingBy(AppropriateStockEntity::getMoveDate, Collectors.reducing(op))));
    }

    @Override
    public void truncateAppropriateStock() {
        this.appropriateStockService.truncateTShopAppropriateStock();
    }

    /**
     * 適正在庫を更新します
     *
     * @param goodsNo          商品番号
     * @param key              ショップ適正在庫の場合：shopNo,倉庫適正在庫の場合：warehouseNo
     * @param appropriateStock 適正在庫
     * @param safetyStock      安全在庫
     */
    @Override
    public void updateAppropriateStock(int goodsNo, int key, BigDecimal appropriateStock, BigDecimal safetyStock) throws Exception {
        appropriateStock = appropriateStock.setScale(3, RoundingMode.HALF_UP);
        safetyStock = safetyStock.setScale(3, RoundingMode.HALF_UP);
        TShopAppropriateStock insertEntity = TShopAppropriateStock.builder().goodsNo(goodsNo).shopNo(key).appropriateStock(appropriateStock).safetyStock(safetyStock).build();
        log.info(String.format("ショップ適正在庫を登録します。goods_no:%d,shopNo:%d,適正在庫:%s,安全在庫:%s", goodsNo, key, appropriateStock, safetyStock));
        this.appropriateStockService.insert(insertEntity);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        super.execute(contribution, chunkContext);
        return RepeatStatus.FINISHED;
    }
}
