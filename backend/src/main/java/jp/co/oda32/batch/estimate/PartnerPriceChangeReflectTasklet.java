package jp.co.oda32.batch.estimate;

import jp.co.oda32.constant.EstimateStatus;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.domain.model.goods.MPartnerGoods;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.service.estimate.TEstimateDetailService;
import jp.co.oda32.domain.service.estimate.TEstimateService;
import jp.co.oda32.domain.service.goods.MPartnerGoodsService;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.util.CollectionUtil;
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
 * 得意先価格変更予定を得意先商品価格に反映するタスクレットクラス
 *
 * @author k_oda
 * @since 2023/02/01
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class PartnerPriceChangeReflectTasklet implements Tasklet {
    @NonNull
    MShopService mShopService;
    @NonNull
    MPartnerGoodsService mPartnerGoodsService;
    @NonNull
    TEstimateService tEstimateService;
    @NonNull
    TEstimateDetailService tEstimateDetailService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // ショップ毎に実行
        List<MShop> mShopServiceList = this.mShopService.findAll();
        for (MShop mShop : mShopServiceList) {
            // 見積のある得意先ごとに実行
            List<TEstimate> reflectEstimateList = this.tEstimateService.findReflectEstimate(mShop.getShopNo(), LocalDate.now());
            // 価格を反映する
            for (TEstimate reflectEstimate : reflectEstimateList) {
                if (updateMPartnerGoodsPrice(reflectEstimate)) {
                    // 見積ステータスを「価格反映済」に更新する
                    reflectEstimate.setEstimateStatus(EstimateStatus.PRICE_REFLECT.getCode());
                    this.tEstimateService.update(reflectEstimate);
                }
            }
        }
        return RepeatStatus.FINISHED;
    }

    private boolean updateMPartnerGoodsPrice(TEstimate reflectEstimate) throws Exception {
        // 得意先番号で得意先商品一覧を取得
        List<MPartnerGoods> mPartnerGoodsList = this.mPartnerGoodsService.find(reflectEstimate.getShopNo(), reflectEstimate.getPartnerNo(), null, null, Flag.NO);

        List<TEstimateDetail> reflectEstimateDetailList = reflectEstimate.getTEstimateDetailList();
        boolean isAllUpdate = true;
        try {
            for (TEstimateDetail reflectEstimateDetail : reflectEstimateDetailList) {
                int destinationNo = reflectEstimate.getDestinationNo() == null ? 0 : reflectEstimate.getDestinationNo();
                List<MPartnerGoods> saveMPartnerGoodsList = mPartnerGoodsList.stream()
                        .filter(mPartnerGoods -> mPartnerGoods.getGoodsNo().equals(reflectEstimateDetail.getGoodsNo()))
                        .filter(mPartnerGoods -> mPartnerGoods.getPartnerNo().equals(reflectEstimate.getPartnerNo()))
                        .filter(mPartnerGoods -> mPartnerGoods.getDestinationNo().equals(destinationNo))
                        .collect(Collectors.toList());
                if (CollectionUtil.isEmpty(saveMPartnerGoodsList)) {
                    // 見積を作っただけなので、得意先商品として登録
                    log.info(String.format("得意先価格を登録。partner_no:%d goods_no:%d destination_no:%d 価格%s", reflectEstimate.getPartnerNo(), reflectEstimateDetail.getGoodsNo(), reflectEstimate.getDestinationNo(), reflectEstimateDetail.getGoodsPrice()));
                    MPartnerGoods mPartnerGoods = MPartnerGoods.builder()
                            .partnerNo(reflectEstimate.getPartnerNo())
                            .goodsNo(reflectEstimateDetail.getGoodsNo())
                            .destinationNo(destinationNo)
                            .goodsPrice(reflectEstimateDetail.getGoodsPrice())
                            .goodsCode(reflectEstimateDetail.getGoodsCode())
                            .goodsName(reflectEstimateDetail.getGoodsName())
                            .companyNo(reflectEstimate.getCompanyNo())
                            .lastPriceUpdateDate(LocalDate.now())
                            .orderNumPerYear(BigDecimal.ZERO)
                            .shopNo(reflectEstimate.getShopNo())
                            .reflectedEstimateNo(reflectEstimateDetail.getEstimateNo())
                            .reflectedEstimateDetailNo(reflectEstimateDetail.getEstimateDetailNo())
                            .build();
                    this.mPartnerGoodsService.insert(mPartnerGoods);
                    // 見積明細の価格反映フラグを立てる
                    reflectEstimateDetail.setPartnerGoodsReflect(true);
                    this.tEstimateDetailService.update(reflectEstimateDetail);
                    continue;
                }
                if (saveMPartnerGoodsList.size() > 1) {
                    // 複数ある場合はPK条件なのでおかしい
                    log.error(String.format("得意先条件のPK設定に問題があります。partner_no:%d goods_no:%d destination_no:%d", reflectEstimate.getPartnerNo(), reflectEstimateDetail.getGoodsNo(), reflectEstimate.getDestinationNo()));
                    isAllUpdate = false;
                    continue;
                }
                // 得意先価格を更新する
                MPartnerGoods updatePartnerGoods = saveMPartnerGoodsList.get(0);
                log.info(String.format("得意先価格を更新。partner_no:%d goods_no:%d destination_no:%d 価格%s→%s", reflectEstimate.getPartnerNo(), reflectEstimateDetail.getGoodsNo(), reflectEstimate.getDestinationNo(), updatePartnerGoods.getGoodsPrice(), reflectEstimateDetail.getGoodsPrice()));
                updatePartnerGoods.setGoodsPrice(reflectEstimateDetail.getGoodsPrice());
                updatePartnerGoods.setLastPriceUpdateDate(LocalDate.now());
                updatePartnerGoods.setReflectedEstimateNo(reflectEstimateDetail.getEstimateNo());
                updatePartnerGoods.setReflectedEstimateDetailNo(reflectEstimateDetail.getEstimateDetailNo());
                this.mPartnerGoodsService.update(updatePartnerGoods);
                // 見積明細の価格反映フラグを立てる
                reflectEstimateDetail.setPartnerGoodsReflect(true);
                this.tEstimateDetailService.update(reflectEstimateDetail);
            }
        } catch (Exception e) {
            log.error(String.format("得意先価格反映バッチでエラー partner_no:%d destination_no:%d errorMassage%s", reflectEstimate.getPartnerNo(), reflectEstimate.getDestinationNo(), e.getMessage()));
            isAllUpdate = false;
        }
        return isAllUpdate;
    }
}
