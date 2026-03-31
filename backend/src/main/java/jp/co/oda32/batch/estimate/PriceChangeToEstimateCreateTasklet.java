package jp.co.oda32.batch.estimate;

import jp.co.oda32.constant.EstimateStatus;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.domain.model.goods.MPartnerGoodsPriceChangePlan;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.service.estimate.TEstimateDetailService;
import jp.co.oda32.domain.service.estimate.TEstimateService;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MPartnerGoodsPriceChangePlanService;
import jp.co.oda32.domain.service.goods.MPartnerGoodsService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.util.DateTimeUtil;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 得意先価格変更予定から見積を作成するタスクレットクラス
 *
 * @author k_oda
 * @since 2022/10/24
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class PriceChangeToEstimateCreateTasklet implements Tasklet {
    @NonNull
    MShopService mShopService;
    @NonNull
    MGoodsService mGoodsService;
    @NonNull
    MPartnerGoodsPriceChangePlanService mPartnerGoodsPriceChangePlanService;
    @NonNull
    MPartnerGoodsService mPartnerGoodsService;
    @NonNull
    TEstimateService tEstimateService;
    @NonNull
    TEstimateDetailService tEstimateDetailService;
    @NonNull
    MPartnerService mPartnerService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // ショップ毎に実行
        List<MShop> mShopServiceList = this.mShopService.findAll();
        for (MShop mShop : mShopServiceList) {
            // 金額変更予定のある得意先ごとに実行
            List<MPartnerGoodsPriceChangePlan> mPartnerGoodsPriceChangePlanList = this.mPartnerGoodsPriceChangePlanService.findEstimateNotCreate(mShop.getShopNo());
            Map<Integer, List<MPartnerGoodsPriceChangePlan>> partnerNoPriceChangePlanMap = mPartnerGoodsPriceChangePlanList.stream()
                    .collect(Collectors.groupingBy(MPartnerGoodsPriceChangePlan::getPartnerNo));
            // 得意先ごとに見積を作成する
            for (Map.Entry<Integer, List<MPartnerGoodsPriceChangePlan>> entry : partnerNoPriceChangePlanMap.entrySet()) {
                createEstimate(mShop.getShopNo(), entry.getKey(), entry.getValue());
            }
        }
        // 見積明細に商品マスタから商品情報をまとめて設定する
        this.tEstimateDetailService.updateGoodsInfo();
        return RepeatStatus.FINISHED;
    }

    protected void createEstimate(int shopNo, int partnerNo, List<MPartnerGoodsPriceChangePlan> partnerGoodsPriceChangePlanList) throws Exception {
        // 得意先を検索する
        MPartner mPartner = this.mPartnerService.getByPartnerNo(partnerNo);
        // 日付、納品先(destination_no)ごとに作成する
        Map<String, List<MPartnerGoodsPriceChangePlan>> changePlanDatePriceChangePlanMap = partnerGoodsPriceChangePlanList.stream()
                .collect(Collectors.groupingBy(mPartnerGoodsPriceChangePlan -> String.format("%s_%d", DateTimeUtil.displayDate(mPartnerGoodsPriceChangePlan.getChangePlanDate())
                        , mPartnerGoodsPriceChangePlan.getDestinationNo() == null ? 0 : mPartnerGoodsPriceChangePlan.getDestinationNo())));

        for (Map.Entry<String, List<MPartnerGoodsPriceChangePlan>> entry : changePlanDatePriceChangePlanMap.entrySet()) {
            List<MPartnerGoodsPriceChangePlan> mPartnerGoodsPriceChangePlanList = entry.getValue();
            try {
                String priceChangeDateStr = entry.getKey().split("_")[0];
                LocalDate priceChangeDate = DateTimeUtil.stringToLocalDate(priceChangeDateStr);
                int destinationNo = Integer.parseInt(entry.getKey().split("_")[1]);
                TEstimate tEstimate = createEstimate(shopNo, mPartner.getCompanyNo(), partnerNo, destinationNo, priceChangeDate);
                List<TEstimateDetail> tEstimateDetailList = createEstimateDetail(tEstimate, mPartnerGoodsPriceChangePlanList);
                for (MPartnerGoodsPriceChangePlan updatePartnerPriceChangePlan : mPartnerGoodsPriceChangePlanList) {
                    updatePartnerPriceChangePlan.setEstimateCreated(true);
                    updatePartnerPriceChangePlan.setEstimateNo(tEstimate.getEstimateNo());
                    int estimateDetailNo = tEstimateDetailList.stream()
                            .filter(tEstimateDetail -> tEstimateDetail.getGoodsNo().equals(updatePartnerPriceChangePlan.getGoodsNo()))
                            .map(TEstimateDetail::getEstimateDetailNo)
                            .findFirst()
                            .orElseThrow(() -> new Exception("対応する見積明細が取得できません。"));
                    updatePartnerPriceChangePlan.setEstimateDetailNo(estimateDetailNo);
                }
                this.mPartnerGoodsPriceChangePlanService.update(mPartnerGoodsPriceChangePlanList);
            } catch (Exception e) {
                // エラーを無視して次のループへ
                log.error(String.format("見積の作成に失敗しました。shopNo:%d,partnerNo:%d,changeDate:%s,原因:%s,%s", shopNo, partnerNo, entry.getKey(), e.getMessage(), e.getCause()));
            }
        }
    }

    protected TEstimate createEstimate(int shopNo, int companyNo, int partnerNo, int destinationNo, LocalDate changePlanDate) throws Exception {
        // 同じ価格変更日の行がある場合はまとめる。ただし、提出済の場合は別で作成する
        List<String> estimateStatusList = Arrays.asList(EstimateStatus.CREATE.getCode(), EstimateStatus.MODIFIED.getCode(), EstimateStatus.BID.getCode(), EstimateStatus.NOT_DEAL.getCode());
        List<TEstimate> estimateList = this.tEstimateService.find(shopNo, partnerNo, estimateStatusList, changePlanDate, Flag.NO);
        TEstimate tEstimate;
        if (estimateList.isEmpty()) {
            // 見積ヘッダテーブル
            tEstimate = TEstimate.builder()
                    .estimateDate(LocalDate.now())// 見積ステータスを「提出（印刷）」するときに修正する
                    .priceChangeDate(changePlanDate)
                    .estimateStatus(EstimateStatus.CREATE.getCode())
                    .shopNo(shopNo)
                    .note(null)
                    .companyNo(companyNo)
                    .partnerNo(partnerNo)
                    .destinationNo(destinationNo)
                    .build();
        } else {
            tEstimate = estimateList.stream().findFirst().get();
            tEstimate.setEstimateStatus(EstimateStatus.MODIFIED.getCode());
        }
        tEstimate = this.tEstimateService.insert(tEstimate);
        return tEstimate;
    }

    private List<TEstimateDetail> createEstimateDetail(TEstimate tEstimate, List<MPartnerGoodsPriceChangePlan> mPartnerGoodsPriceChangePlanList) throws Exception {
        // 見積明細テーブル
        List<TEstimateDetail> insertDetailList = convertPartnerGoodsPricePlan(tEstimate.getEstimateNo(), mPartnerGoodsPriceChangePlanList);
        // 一旦明細を削除して入れなおす
        this.tEstimateDetailService.delete(tEstimate.getEstimateNo());
        return this.tEstimateDetailService.insert(insertDetailList);
    }

    private List<TEstimateDetail> convertPartnerGoodsPricePlan(int estimateNo, List<MPartnerGoodsPriceChangePlan> mPartnerGoodsPriceChangePlanList) {
        List<TEstimateDetail> existTEstimateDetailList = this.tEstimateDetailService.findByEstimateNo(estimateNo);
        List<TEstimateDetail> tEstimateDetailList = new ArrayList<>();
        int estimateDetailNo = 1;
        if (!existTEstimateDetailList.isEmpty()) {
            // 金額再調整の場合があるので、同じ商品見積を避けるため、既存の見積に今回見積追加するものがあれば削除しておく
            List<Integer> estimateGoodsNoList = mPartnerGoodsPriceChangePlanList.stream().map(MPartnerGoodsPriceChangePlan::getGoodsNo).collect(Collectors.toList());
            existTEstimateDetailList.removeIf(tEstimateDetail -> estimateGoodsNoList.contains(tEstimateDetail.getGoodsNo()));
            tEstimateDetailList.addAll(existTEstimateDetailList);
            estimateDetailNo = 1 + existTEstimateDetailList.size();
        }
        for (MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan : mPartnerGoodsPriceChangePlanList) {
            BigDecimal containNum = BigDecimal.ONE;
            if (mPartnerGoodsPriceChangePlan.getChangeContainNum() != null) {
                containNum = mPartnerGoodsPriceChangePlan.getChangeContainNum();
            } else {
                containNum = this.mGoodsService.getByGoodsNo(mPartnerGoodsPriceChangePlan.getGoodsNo()).getCaseContainNum();
            }
            TEstimateDetail tEstimateDetail = TEstimateDetail.builder()
                    .estimateNo(estimateNo)
                    .estimateDetailNo(estimateDetailNo)
                    .companyNo(mPartnerGoodsPriceChangePlan.getCompanyNo())
                    .goodsPrice(mPartnerGoodsPriceChangePlan.getAfterPrice())
                    .goodsNo(mPartnerGoodsPriceChangePlan.getGoodsNo())
                    .goodsCode(mPartnerGoodsPriceChangePlan.getGoodsCode())
                    .goodsName(mPartnerGoodsPriceChangePlan.getGoodsName())
                    .changeContainNum(mPartnerGoodsPriceChangePlan.getChangeContainNum())
                    .shopNo(mPartnerGoodsPriceChangePlan.getShopNo())
                    .containNum(containNum)
                    .detailNote(String.format("現単価%s円", mPartnerGoodsPriceChangePlan.getBeforePrice().stripTrailingZeros().toPlainString()))
                    .build();
            if (mPartnerGoodsPriceChangePlan.isDeficitFlg()) {
                // 赤字見積は備考に修正しないといけないことを明示する
                tEstimateDetail.setDetailNote(String.format("【赤字です】価格修正してください。%s", tEstimateDetail.getDetailNote()));
            }
            tEstimateDetailList.add(tEstimateDetail);
            estimateDetailNo++;
        }
        return tEstimateDetailList;
    }
}
