package jp.co.oda32.batch.estimate;

import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.domain.model.goods.MPartnerGoodsPriceChangePlan;
import jp.co.oda32.domain.service.estimate.TEstimateDetailService;
import jp.co.oda32.domain.service.goods.MPartnerGoodsPriceChangePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【修正パッチ】一時的に作成。得意先価格修正マスタの見積番号、見積明細番号を再振り分けするタスクレットクラス
 *
 * @author k_oda
 * @since 2023/01/23
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class ModifyEstimateNoMPartnerGoodsTasklet implements Tasklet {
    @NonNull
    TEstimateDetailService tEstimateDetailService;
    @NonNull
    MPartnerGoodsPriceChangePlanService mPartnerGoodsPriceChangePlanService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 得意先価格変更マスタ全抽出
        List<MPartnerGoodsPriceChangePlan> mPartnerGoodsPriceChangePlanList = this.mPartnerGoodsPriceChangePlanService.findAll();
        for (MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan : mPartnerGoodsPriceChangePlanList) {
            // 見積明細を検索
            List<TEstimateDetail> tEstimateDetailList = this.tEstimateDetailService.findByGoodsAndCompany(mPartnerGoodsPriceChangePlan.getGoodsNo(), mPartnerGoodsPriceChangePlan.getCompanyNo());
            if (tEstimateDetailList.size() == 0) {
                System.out.printf("該当する見積が見つかりません。no:%d,goods_code:%s,goods_name:%s,company_no:%d,partner_code:%s,partner_name:%s%n"
                        , mPartnerGoodsPriceChangePlan.getPartnerGoodsPriceChangePlanNo()
                        , mPartnerGoodsPriceChangePlan.getGoodsCode()
                        , mPartnerGoodsPriceChangePlan.getGoodsName()
                        , mPartnerGoodsPriceChangePlan.getCompanyNo()
                        , mPartnerGoodsPriceChangePlan.getPartnerCode()
                        , mPartnerGoodsPriceChangePlan.getMPartner().getPartnerName());
                mPartnerGoodsPriceChangePlan.setEstimateNo(null);
                mPartnerGoodsPriceChangePlan.setEstimateDetailNo(null);
                mPartnerGoodsPriceChangePlan.setEstimateCreated(false);
                this.mPartnerGoodsPriceChangePlanService.update(mPartnerGoodsPriceChangePlan);
//                continue;
            }
//            if (tEstimateDetailList.size() == 1) {
//                mPartnerGoodsPriceChangePlan.setEstimateNo(tEstimateDetailList.get(0).getEstimateNo());
//                mPartnerGoodsPriceChangePlan.setEstimateDetailNo(tEstimateDetailList.get(0).getEstimateDetailNo());
//                this.mPartnerGoodsPriceChangePlanService.update(mPartnerGoodsPriceChangePlan);
//                continue;
//            }
//            TEstimateDetail tEstimateDetail = tEstimateDetailList.stream().max(Comparator.comparing(TEstimateDetail::getEstimateNo)).get();
//            mPartnerGoodsPriceChangePlan.setEstimateNo(tEstimateDetail.getEstimateNo());
//            mPartnerGoodsPriceChangePlan.setEstimateDetailNo(tEstimateDetail.getEstimateDetailNo());
//            this.mPartnerGoodsPriceChangePlanService.update(mPartnerGoodsPriceChangePlan);
        }
        return RepeatStatus.FINISHED;
    }
}
