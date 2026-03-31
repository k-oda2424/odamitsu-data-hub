package jp.co.oda32.batch.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MPartnerGoods;
import jp.co.oda32.domain.model.goods.MPartnerGoodsPriceChangePlan;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.service.goods.MPartnerGoodsPriceChangePlanService;
import jp.co.oda32.domain.service.goods.MPartnerGoodsService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.domain.service.purchase.MPurchasePriceChangePlanService;
import jp.co.oda32.util.BigDecimalUtil;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 仕入価格変更予定から得意先価格変更予定を作成するタスクレットクラス
 *
 * @author k_oda
 * @since 2022/10/19
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class PartnerPriceChangePlanCreateTasklet implements Tasklet {
    @NonNull
    MShopService mShopService;
    @NonNull
    MPurchasePriceChangePlanService mPurchasePriceChangePlanService;
    @NonNull
    MPartnerGoodsPriceChangePlanService mPartnerGoodsPriceChangePlanService;
    @NonNull
    MPartnerService mPartnerService;
    @NonNull
    MPartnerGoodsService mPartnerGoodsService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // ショップ毎に実行
        List<MShop> mShopServiceList = this.mShopService.findAll();
        for (MShop mShop : mShopServiceList) {
            // 仕入金額変更予定検索
            List<MPurchasePriceChangePlan> purchasePriceChangePlanList = mPurchasePriceChangePlanService.findPartnerPriceChangePlanNotCreate(mShop.getShopNo());
            // 該当商品1件ずつ、注文履歴のある得意先の価格変更情報を作成する
            for (MPurchasePriceChangePlan mPurchasePriceChangePlan : purchasePriceChangePlanList) {
                this.createPartnerPriceChangePlan(mPurchasePriceChangePlan);
            }
        }
        // 商品番号をまとめて設定、更新
        this.mPartnerGoodsPriceChangePlanService.updateGoodsNo();
        return RepeatStatus.FINISHED;
    }

    /**
     * 仕入商品価格改定予定から各得意先の価格変更予定を作成します
     *
     * @param purchasePriceChangePlan 仕入価格変更予定Entity
     */
    @Transactional
    protected void createPartnerPriceChangePlan(MPurchasePriceChangePlan purchasePriceChangePlan) {
        List<MPartner> childPartners = null;
        if (purchasePriceChangePlan.getPartnerNo() != 0) {
            // Find the partners where parentPartnerNo is the given partnerNo
            childPartners = mPartnerService.findByParentPartnerNo(purchasePriceChangePlan.getPartnerNo());
        }
        List<Integer> childPartnerNos = new ArrayList<>();
        if (childPartners != null) {
            // Get their partnerNos
            childPartnerNos = childPartners.stream().map(MPartner::getPartnerNo).collect(Collectors.toList());
            // Add the original partnerNo to the list
            childPartnerNos.add(purchasePriceChangePlan.getPartnerNo());
        }

        List<MPartnerGoods> mPartnerGoodsList;
        if (CollectionUtil.isEmpty(childPartnerNos)) {
            // 得意先商品を検索する
            mPartnerGoodsList = this.mPartnerGoodsService.find(purchasePriceChangePlan.getShopNo()
                    , purchasePriceChangePlan.getPartnerNo()
                    , purchasePriceChangePlan.getGoodsCode()
                    , purchasePriceChangePlan.getDestinationNo()
                    , Flag.NO);
        } else {
            mPartnerGoodsList = this.mPartnerGoodsService.find(purchasePriceChangePlan.getShopNo()
                    , childPartnerNos
                    , purchasePriceChangePlan.getGoodsCode()
                    , purchasePriceChangePlan.getDestinationNo()
                    , Flag.NO);
        }
        List<Integer> goodsNoList = mPartnerGoodsList.stream().map(MPartnerGoods::getGoodsNo).distinct().collect(Collectors.toList());
        // 既存の得意先商品価格変更予定レコード(まとめて検索)
        List<MPartnerGoodsPriceChangePlan> existPartnerPriceChangePlanList = this.mPartnerGoodsPriceChangePlanService.findParentGoodsPriceChangePlanNotCreated(purchasePriceChangePlan.getShopNo()
                , goodsNoList
                , purchasePriceChangePlan.getPartnerNo()
                , purchasePriceChangePlan.getDestinationNo()
                , purchasePriceChangePlan.getChangePlanDate());
        for (MPartnerGoods mPartnerGoods : mPartnerGoodsList) {
            if (BigDecimalUtil.isZero(mPartnerGoods.getGoodsPrice())) {
                // 売価が0円のものは省く
                continue;
            }
            BigDecimal beforeProfitRate = mPartnerGoods.getGoodsPrice().divide(purchasePriceChangePlan.getBeforePrice(), 10, RoundingMode.HALF_UP);
            // 新価格 = 新仕入価格(代替商品の可能性がある) × 今の利益率
            BigDecimal afterPrice = purchasePriceChangePlan.getAfterPrice().multiply(beforeProfitRate).setScale(0, RoundingMode.HALF_UP);
            MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan = MPartnerGoodsPriceChangePlan.builder()
                    .shopNo(purchasePriceChangePlan.getShopNo())
                    .companyNo(mPartnerGoods.getCompanyNo())
                    .partnerNo(mPartnerGoods.getMCompany().getPartnerNo())
                    .partnerCode(mPartnerGoods.getMCompany().getPartner().getPartnerCode())
                    .destinationNo(purchasePriceChangePlan.getDestinationNo())
                    .changePlanDate(purchasePriceChangePlan.getChangePlanDate())
                    .goodsCode(purchasePriceChangePlan.getGoodsCode())
                    .beforePurchasePrice(purchasePriceChangePlan.getBeforePrice())
                    .afterPurchasePrice(purchasePriceChangePlan.getAfterPrice())
                    .changeReason(purchasePriceChangePlan.getChangeReason())
                    .goodsName(mPartnerGoods.getGoodsName())
                    .janCode(purchasePriceChangePlan.getJanCode())
                    .beforePrice(mPartnerGoods.getGoodsPrice())
                    .afterPrice(afterPrice)
                    .changeContainNum(purchasePriceChangePlan.getChangeContainNum())
                    .build();
            // 利益率
            BigDecimal nowProfitRate = BigDecimal.ONE.subtract(purchasePriceChangePlan.getAfterPrice().divide(afterPrice, 4, RoundingMode.HALF_UP));
            if (BigDecimalUtil.isNegative(nowProfitRate) || BigDecimalUtil.isZero(nowProfitRate)) {
                // 赤字になっている
                log.warn(String.format("得意先商品価格変更予定登録で赤字です。shopNo:%d,得意先コード:%s,商品コード:%s,得意先価格:%s　仕入価格:%s,価格変更日:%s", purchasePriceChangePlan.getShopNo(), mPartnerGoodsPriceChangePlan.getPartnerCode(), purchasePriceChangePlan.getGoodsCode(), afterPrice, purchasePriceChangePlan.getAfterPrice(), mPartnerGoodsPriceChangePlan.getChangePlanDate()));
                mPartnerGoodsPriceChangePlan.setDeficitFlg(true);
                mPartnerGoodsPriceChangePlan.setNote(String.format("赤字になっています。商品コード:%s,得意先価格:%s　仕入価格:%s,価格変更日:%s", purchasePriceChangePlan.getGoodsCode(), afterPrice, purchasePriceChangePlan.getAfterPrice(), mPartnerGoodsPriceChangePlan.getChangePlanDate()));
            }
            // 既存レコード
            MPartnerGoodsPriceChangePlan existPartnerPriceChangePlan = existPartnerPriceChangePlanList.stream()
                    .filter(existPlan -> Objects.equals(existPlan.getPartnerNo(), mPartnerGoods.getMCompany().getPartnerNo()))
                    .filter(existPlan -> Objects.equals(existPlan.getGoodsNo(), mPartnerGoods.getGoodsNo()))
                    .findFirst().orElse(null);
            if (existPartnerPriceChangePlan != null) {
                mPartnerGoodsPriceChangePlan.setPartnerGoodsPriceChangePlanNo(existPartnerPriceChangePlan.getPartnerGoodsPriceChangePlanNo());
                // 得意先専用見積の場合は更新しない
                if (!Objects.equals(existPartnerPriceChangePlan.getPartnerNo(), mPartnerGoodsPriceChangePlan.getPartnerNo()) || !Objects.equals(existPartnerPriceChangePlan.getDestinationNo(), mPartnerGoodsPriceChangePlan.getDestinationNo())) {
                    // 完全に両方合致しない場合、別見積扱いとする。完全一致した場合のみ更新対象とする
                    this.insertMPartnerGoodsPriceChange(mPartnerGoodsPriceChangePlan, purchasePriceChangePlan);
                } else {
                    // 得意先価格変更予定を更新する
                    this.updateMPartnerGoodsPriceChange(mPartnerGoodsPriceChangePlan, purchasePriceChangePlan);
                }
            } else {
                // 得意先価格変更予定を登録する
                this.insertMPartnerGoodsPriceChange(mPartnerGoodsPriceChangePlan, purchasePriceChangePlan);
            }
        }
        // 得意先商品価格変更予定登録フラグを立てて仕入先価格変更予定マスタを更新
        purchasePriceChangePlan.setPartnerPriceChangePlanCreated(true);
        try {
            this.mPurchasePriceChangePlanService.update(purchasePriceChangePlan);
        } catch (Exception e) {
            log.error(String.format("仕入先価格変更予定登録に失敗しました。goods_code:%s,shopNo:%d,reason:%s：%s", purchasePriceChangePlan.getGoodsCode(), purchasePriceChangePlan.getShopNo(), e.getMessage(), e.getCause()));
        }
    }

    private void insertMPartnerGoodsPriceChange(MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan, MPurchasePriceChangePlan purchasePriceChangePlan) {
        try {
            this.mPartnerGoodsPriceChangePlanService.insert(mPartnerGoodsPriceChangePlan);
            log.info(String.format("得意先商品価格変更予定を登録しました。shopNo:%d,得意先コード:%s,goods_code:%s,得意先価格:%s　仕入価格:%s,価格変更日:%s", purchasePriceChangePlan.getShopNo(), mPartnerGoodsPriceChangePlan.getPartnerCode(), purchasePriceChangePlan.getGoodsCode(), mPartnerGoodsPriceChangePlan.getAfterPrice(), purchasePriceChangePlan.getAfterPrice(), mPartnerGoodsPriceChangePlan.getChangePlanDate()));
        } catch (Exception e) {
            log.error(String.format("得意先商品価格変更予定登録に失敗しました。goods_code:%s,shopNo:%d,得意先コード:%s,reason:%s：%s", purchasePriceChangePlan.getGoodsCode(), purchasePriceChangePlan.getShopNo(), mPartnerGoodsPriceChangePlan.getPartnerCode(), e.getMessage(), e.getCause()));
        }
    }

    private void updateMPartnerGoodsPriceChange(MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan, MPurchasePriceChangePlan purchasePriceChangePlan) {
        try {
            this.mPartnerGoodsPriceChangePlanService.update(mPartnerGoodsPriceChangePlan);
            log.info(String.format("得意先商品価格変更予定を更新しました。shopNo:%d,得意先コード:%s,goods_code:%s,得意先価格:%s　仕入価格:%s,価格変更日:%s", purchasePriceChangePlan.getShopNo(), mPartnerGoodsPriceChangePlan.getPartnerCode(), purchasePriceChangePlan.getGoodsCode(), mPartnerGoodsPriceChangePlan.getAfterPrice(), purchasePriceChangePlan.getAfterPrice(), mPartnerGoodsPriceChangePlan.getChangePlanDate()));
        } catch (Exception e) {
            log.error(String.format("得意先商品価格変更予定更新に失敗しました。goods_code:%s,shopNo:%d,得意先コード:%s,reason:%s：%s", purchasePriceChangePlan.getGoodsCode(), purchasePriceChangePlan.getShopNo(), mPartnerGoodsPriceChangePlan.getPartnerCode(), e.getMessage(), e.getCause()));
        }
    }
}
