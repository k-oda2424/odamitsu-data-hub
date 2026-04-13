package jp.co.oda32.batch.stock;

import jp.co.oda32.dto.stock.AppropriateStockEntity;
import jp.co.oda32.domain.model.embeddable.MSalesGoodsPK;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 適正在庫を算出するタスク抽象クラス
 * 適正在庫 = リードタイム×1日平均出荷数量＋安全在庫(変動対応在庫)
 *
 * @author k_oda
 * @since 2019/05/20
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public abstract class AppropriateStockCalculateTasklet implements Tasklet, IAppropriateStockCalculate {
    // ショップ番号,商品番号とリードタイムのマップ
    protected Map<MSalesGoodsPK, Integer> leadTimeMap = new HashMap<>();
    @Value("#{jobParameters['spanMonths'] ?: 1}")
    protected Integer spanMonths;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 対象テーブルの各値を0にする
        truncateAppropriateStock();
        // 商品毎,日付毎にまとめる
        Map<Integer, Map<LocalDate, Optional<AppropriateStockEntity>>> targetMap = getTargetMap();
        // 期間日数
        int periodDays = (int) ChronoUnit.DAYS.between(LocalDate.now().minusMonths(this.spanMonths), LocalDate.now());
        log.info(String.format("対象商品件数:%d件", targetMap.keySet().size()));
        // 商品毎に適正在庫などを計算する
        targetMap.forEach((goodsNo, dataMap) -> {
            List<AppropriateStockEntity> targetDataList = dataMap.values().stream().map(Optional::get).collect(Collectors.toList());
            AppropriateStockEntity appropriateStockEntity = targetDataList.get(0);
            int leadTime = appropriateStockEntity.getLeadTime();
            AppropriateStockCalculate appropriateStockCalculate = new AppropriateStockCalculate(leadTime);
            BigDecimal standardDeviation = appropriateStockCalculate.calculateStandardDeviation(targetDataList, periodDays);
            BigDecimal safetyStock = appropriateStockCalculate.calculateSafetyStock(standardDeviation);
            BigDecimal ave = targetDataList.stream().map(AppropriateStockEntity::getGoodsNum).reduce(BigDecimal.ZERO, BigDecimal::add).divide(new BigDecimal(periodDays), 32, RoundingMode.HALF_UP);
            BigDecimal appropriateStock = appropriateStockCalculate.calculateAppropriateStock(ave, safetyStock);
            try {
                updateAppropriateStock(appropriateStockEntity.getGoodsNo(), appropriateStockEntity.getShopNo(), appropriateStock, safetyStock);
            } catch (Exception e) {
                e.printStackTrace();
                log.error(String.format("適正在庫更新処理に失敗しました。商品番号:%d,ショップ番号:%d,倉庫番号:%d", goodsNo, appropriateStockEntity.getShopNo(), appropriateStockEntity.getWarehouseNo()));
            }
        });
        return RepeatStatus.FINISHED;
    }
}
