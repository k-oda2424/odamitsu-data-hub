package jp.co.oda32.batch.stock;

import jp.co.oda32.dto.stock.AppropriateStockEntity;
import jp.co.oda32.domain.model.order.TOrderDetail;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 倉庫適正在庫を算出するタスククラス
 * 適正在庫 = リードタイム×1日平均出荷数量＋安全在庫(変動対応在庫)
 *
 * @author k_oda
 * @since 2019/05/20
 */
@Component
@Log4j2
@StepScope
public class WarehouseAppropriateStockCalculateTasklet extends AppropriateStockCalculateTasklet {
    Function<TOrderDetail, AppropriateStockEntity> sumOrderDetail = (orderDetail) -> AppropriateStockEntity.builder()
            .goodsNum(orderDetail.getOrderNum().subtract(orderDetail.getCancelNum()).subtract(orderDetail.getReturnNum()))
            .goodsNo(orderDetail.getGoodsNo())
            .moveDate(orderDetail.getOrderDate())
            .leadTime(7)
            .build();


    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        super.execute(contribution, chunkContext);
        return RepeatStatus.FINISHED;
    }

    /**
     * リードタイムを取得します。
     *
     * @param goodsNo 商品番号
     * @param pk      販売商品から検索する場合shopNo,在庫から検索する場合はwarehouseNo
     * @return 商品のリードタイム
     */
    @Override
    public int getLeadTime(int goodsNo, int pk) {
        return 0;
    }

    @Override
    public Map<Integer, Map<LocalDate, Optional<AppropriateStockEntity>>> getTargetMap() {
        // 適正在庫対象商品の抽出 移動日が(引数の数字)ヶ月以内
        LocalDateTime moveDateFrom = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusMonths(12);
//        List<TStockLog> stockLogList = this.tStockLogService.find();
//        // 商品毎,日付毎にまとめる
//        return orderDetailList.stream()
//                .map(sumOrderDetail)
//                .collect(Collectors.groupingBy(AppropriateStockEntity::getGoodsNo, Collectors.groupingBy(AppropriateStockEntity::getMoveDate, Collectors.reducing(op))));
        return null;
    }

    @Override
    public void truncateAppropriateStock() {
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

    }
}
