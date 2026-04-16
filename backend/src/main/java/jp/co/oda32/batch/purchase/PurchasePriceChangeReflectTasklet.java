package jp.co.oda32.batch.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MTaxRate;
import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.service.CommonService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.domain.service.purchase.MPurchasePriceChangePlanService;
import jp.co.oda32.domain.service.purchase.MPurchasePriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 仕入価格変更予定を仕入価格に反映するタスクレットクラス
 *
 * @author k_oda
 * @since 2023/06/28
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class PurchasePriceChangeReflectTasklet implements Tasklet {
    @NonNull
    MShopService mShopService;
    @NonNull
    MPurchasePriceChangePlanService mPurchasePriceChangePlanService;
    @NonNull
    MPurchasePriceService mPurchasePriceService;
    @NonNull
    WSalesGoodsService wSalesGoodsService;
    @NonNull
    CommonService commonService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 仕入価格変更予定の仕入価格反映していない行を取得
        List<MPurchasePriceChangePlan> reflectEstimateList = this.mPurchasePriceChangePlanService.findByPurchasePriceReflectFalse();
        // 仕入価格変更予定日付が過ぎているか
        reflectEstimateList = reflectEstimateList.stream()
                .filter(plan -> (plan.getChangePlanDate().isBefore(LocalDate.now()) || plan.getChangePlanDate().isEqual(LocalDate.now()))).collect(Collectors.toList());
        // 仕入価格に反映する
        for (MPurchasePriceChangePlan reflectPurchasePricePlan : reflectEstimateList) {
            this.updateMPurchasePrice(reflectPurchasePricePlan);
        }
        // 仕入価格変更予定の反映フラグを立てる
        this.mPurchasePriceChangePlanService.updateReflectComplete();
        return RepeatStatus.FINISHED;
    }

    private void updateMPurchasePrice(MPurchasePriceChangePlan reflectPurchasePricePlan) {
        if (reflectPurchasePricePlan.getMSupplier() == null) {
            log.warn(String.format("仕入先が見つかりません。仕入先コードを確認してください。仕入先ｺｰﾄﾞ:%s", reflectPurchasePricePlan.getSupplierCode()));
            return;
        }
        // 販売商品の検索
        WSalesGoods goods = this.wSalesGoodsService.getByShopNoAndGoodsCode(reflectPurchasePricePlan.getShopNo(), reflectPurchasePricePlan.getGoodsCode());
        if (goods == null) {
            log.error(String.format("仕入価格更新バッチ 商品が見つかりません。shop_no:%d goods_code:%s", reflectPurchasePricePlan.getShopNo(), reflectPurchasePricePlan.getGoodsCode()));
            return;
        }
        // 仕入価格マスタの検索
        try {
            MPurchasePrice targetPurchase = this.mPurchasePriceService.getByUK(reflectPurchasePricePlan.getMSupplier().getSupplierNo(), reflectPurchasePricePlan.getShopNo(), goods.getGoodsNo(), reflectPurchasePricePlan.getPartnerNo(), reflectPurchasePricePlan.getDestinationNo());
            if (targetPurchase == null) {
                log.warn(String.format("仕入価格変更予定があるのに仕入価格にレコードがありません。仕入価格レコードを作成します。商品コード:%s 商品名:%s 前:%s 後:%s", reflectPurchasePricePlan.getGoodsCode(), reflectPurchasePricePlan.getGoodsName(), reflectPurchasePricePlan.getBeforePrice(), reflectPurchasePricePlan.getAfterPrice()));
                // 仕入価格レコード作成
                MTaxRate mTaxRate = this.commonService.getMTaxRate();
                MGoods mGoods = goods.getMGoods();
                if (mGoods == null) {
                    // 商品マスタがない → 適正な税率・税区分が決定不能なので skip
                    log.warn("商品マスタ (m_goods) が存在しないため仕入価格レコード作成をスキップします。商品番号:{} 商品コード:{}",
                            goods.getGoodsNo(), reflectPurchasePricePlan.getGoodsCode());
                    return;
                }
                BigDecimal taxRate = mTaxRate.getTaxRate();
                if (mGoods.isApplyReducedTaxRate()) {
                    taxRate = mTaxRate.getReducedTaxRate();
                }
                MPurchasePrice mPurchasePrice = MPurchasePrice.builder()
                        .supplierNo(reflectPurchasePricePlan.getMSupplier().getSupplierNo())
                        .shopNo(reflectPurchasePricePlan.getShopNo())
                        .goodsNo(goods.getGoodsNo())
                        .partnerNo(reflectPurchasePricePlan.getPartnerNo())
                        .destinationNo(reflectPurchasePricePlan.getDestinationNo())
                        .goodsPrice(reflectPurchasePricePlan.getAfterPrice())
                        .taxRate(taxRate)
                        .taxCategory(mGoods.getTaxCategory())
                        .includeTaxFlg(Flag.NO.getValue())
                        .build();
                this.mPurchasePriceService.save(mPurchasePrice);
                return;
            }
            log.info(String.format("仕入価格を更新します。商品番号:%d 商品コード:%s 商品名:%s 前:%s 後:%s", goods.getGoodsNo(), reflectPurchasePricePlan.getGoodsCode(), reflectPurchasePricePlan.getGoodsName(), targetPurchase.getGoodsPrice(), reflectPurchasePricePlan.getAfterPrice()));
            // 仕入価格更新
            targetPurchase.setGoodsPrice(reflectPurchasePricePlan.getAfterPrice());
            this.mPurchasePriceService.save(targetPurchase);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }
}
