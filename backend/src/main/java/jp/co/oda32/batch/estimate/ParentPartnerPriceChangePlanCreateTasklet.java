package jp.co.oda32.batch.estimate;

import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.domain.model.goods.MPartnerGoodsPriceChangePlan;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.service.estimate.TEstimateDetailService;
import jp.co.oda32.domain.service.estimate.TEstimateService;
import jp.co.oda32.domain.service.goods.MPartnerGoodsPriceChangePlanService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.util.BigDecimalUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 得意先価格変更予定から親得意先価格変更を作成するタスクレットクラス
 *
 * @author k_oda
 * @since 2022/12/29
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class ParentPartnerPriceChangePlanCreateTasklet implements Tasklet {
    @NonNull
    MPartnerService mPartnerService;
    @NonNull
    MPartnerGoodsPriceChangePlanService mPartnerGoodsPriceChangePlanService;
    @NonNull
    TEstimateDetailService tEstimateDetailService;
    @NonNull
    TEstimateService tEstimateService;
    BiFunction<MPartnerGoodsPriceChangePlan, List<MPartner>, CustomParentGoodsPriceChange> convertCustomPartnerGoodsPriceChange =
            (mPartnerGoodsPriceChangePlan, parentPartnerList) -> {
                CustomParentGoodsPriceChange parentGoodsPriceChange = new CustomParentGoodsPriceChange();
                BeanUtils.copyProperties(mPartnerGoodsPriceChangePlan, parentGoodsPriceChange);
                // 得意先番号と得意先コードを親得意先のものにセットする（PartnerNo と PartnerCode は常にペアで設定）
                Integer parentPartnerNo = mPartnerGoodsPriceChangePlan.getMPartner().getParentPartnerNo();
                parentGoodsPriceChange.setCParentPartnerNo(parentPartnerNo);
                parentPartnerList.stream()
                        .filter(p -> p.getPartnerNo().equals(parentPartnerNo))
                        .findFirst()
                        .ifPresent(p -> parentGoodsPriceChange.setCParentPartnerCode(p.getPartnerCode()));
                parentGoodsPriceChange.setEstimateCreated(false);
                parentGoodsPriceChange.setPartnerPriceReflect(false);
                // 新規登録するためPKをnullにする
                parentGoodsPriceChange.setPartnerGoodsPriceChangePlanNo(null);
                parentGoodsPriceChange.setAddUserNo(null);
                parentGoodsPriceChange.setAddDateTime(null);
                parentGoodsPriceChange.setModifyUserNo(null);
                parentGoodsPriceChange.setModifyDateTime(null);
                return parentGoodsPriceChange;
            };

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 親得意先設定のある得意先を取得
        List<MPartner> hasParentPartnerList = this.mPartnerService.findHasParentPartner();
        List<Integer> parentPartnerNoList = hasParentPartnerList.stream().map(MPartner::getParentPartnerNo).collect(Collectors.toList());
        List<MPartner> parentPartnerList = this.mPartnerService.findByPartnerNoList(parentPartnerNoList);
        // 価格変更予定情報取得
        List<Integer> childrenPartnerNoList = hasParentPartnerList.stream().map(MPartner::getPartnerNo).collect(Collectors.toList());
        List<MPartnerGoodsPriceChangePlan> childrenPriceChangeList = this.mPartnerGoodsPriceChangePlanService.findParentGoodsPriceChangePlanNotCreated(childrenPartnerNoList);
        List<CustomParentGoodsPriceChange> parentPriceChangeCreateList = childrenPriceChangeList.stream()
                .map(childrenPriceChange -> convertCustomPartnerGoodsPriceChange.apply(childrenPriceChange, parentPartnerList))
                .collect(Collectors.toList());
        // 更新しないPKをListとして保持
        List<Integer> removeList = new ArrayList<>();
        // 登録する親得意先商品価格変更予定Entity保持用
        List<CustomParentGoodsPriceChange> insertParentGoodsPriceChangePlanList = new ArrayList<>();
        // 親得意先,商品毎にまとめる
        Map<Integer, Map<Integer, Optional<CustomParentGoodsPriceChange>>> changeListMap = parentPriceChangeCreateList.stream()
                .collect(Collectors.groupingBy(CustomParentGoodsPriceChange::getCParentPartnerNo,
                        Collectors.groupingBy(CustomParentGoodsPriceChange::getGoodsNo
                                , Collectors.maxBy(Comparator.comparing(CustomParentGoodsPriceChange::getAfterPrice).thenComparing(CustomParentGoodsPriceChange::getChangePlanDate)))));
        for (Map.Entry<Integer, Map<Integer, Optional<CustomParentGoodsPriceChange>>> parentNoEntry : changeListMap.entrySet()) {
            // 得意先のループ
            Map<Integer, Optional<CustomParentGoodsPriceChange>> goodsMap = parentNoEntry.getValue();
            for (Map.Entry<Integer, Optional<CustomParentGoodsPriceChange>> goodsEntry : goodsMap.entrySet()) {
                // 商品番号毎のループ
                Optional<CustomParentGoodsPriceChange> vParentGoodsPriceChangeOptional = goodsEntry.getValue();
                if (!vParentGoodsPriceChangeOptional.isPresent()) {
                    // 商品情報が取れない
                    removeList.addAll(parentPriceChangeCreateList.stream()
                            .filter(vParentGoodsPriceChange -> vParentGoodsPriceChange.getCParentPartnerNo().equals(parentNoEntry.getKey()))
                            .filter(vParentGoodsPriceChange -> vParentGoodsPriceChange.getGoodsNo().equals(goodsEntry.getKey()))
                            .map(CustomParentGoodsPriceChange::getPartnerGoodsPriceChangePlanNo)
                            .collect(Collectors.toList()));
                    continue;
                }
                CustomParentGoodsPriceChange vParentGoodsPriceChange = vParentGoodsPriceChangeOptional.get();
                insertParentGoodsPriceChangePlanList.add(vParentGoodsPriceChange);
            }
        }
        // 親得意先商品価格変更予定を新規登録（removeList に該当する PK は登録対象から除外）
        if (!removeList.isEmpty()) {
            insertParentGoodsPriceChangePlanList = insertParentGoodsPriceChangePlanList.stream()
                    .filter(insertParentGoodsPriceChangePlan -> removeList.stream().noneMatch(removeNo -> removeNo.equals(insertParentGoodsPriceChangePlan.getPartnerGoodsPriceChangePlanNo())))
                    .collect(Collectors.toList());
        }
        // 重複を省く
        insertParentGoodsPriceChangePlanList = insertParentGoodsPriceChangePlanList.stream()
                .distinct()
                .collect(Collectors.toList());
        for (CustomParentGoodsPriceChange insertCustomParentGoodsPriceChange : insertParentGoodsPriceChangePlanList) {
            MPartnerGoodsPriceChangePlan insertParentGoodsPriceChangePlan = convertParentGoodsPriceChange(insertCustomParentGoodsPriceChange);
            MPartnerGoodsPriceChangePlan insertedParentPlan = this.mPartnerGoodsPriceChangePlanService.insert(insertParentGoodsPriceChangePlan);
            // 元の行に登録した親得意先番号を設定して更新
            List<MPartnerGoodsPriceChangePlan> updateChildrenChangePlanList = childrenPriceChangeList.stream()
                    .filter(childrenPriceChange -> Objects.equals(childrenPriceChange.getMPartner().getParentPartnerNo(), insertedParentPlan.getPartnerNo()))
                    .filter(childrenPriceChange -> Objects.equals(childrenPriceChange.getGoodsNo(), insertedParentPlan.getGoodsNo()))
                    .peek(childrenPriceChange -> childrenPriceChange.setParentChangePlanNo(insertedParentPlan.getPartnerGoodsPriceChangePlanNo()))
                    .collect(Collectors.toList());
            this.mPartnerGoodsPriceChangePlanService.update(updateChildrenChangePlanList);
            // 金額が変更となって場合に対応するため、登録した日付と価格を同一グループの子得意先の価格変更予定に反映する
            List<MPartnerGoodsPriceChangePlan> updatePriceChildrenChangePlanList = updateChildrenChangePlanList.stream()
                    .filter(childrenPriceChange -> childrenPriceChange.getGoodsNo().equals(insertParentGoodsPriceChangePlan.getGoodsNo()))
                    .filter(childrenPriceChange -> !BigDecimalUtil.isEqual(childrenPriceChange.getAfterPrice(), insertParentGoodsPriceChangePlan.getAfterPrice()))
                    .peek(childrenPriceChange -> childrenPriceChange.setAfterPrice(insertParentGoodsPriceChangePlan.getAfterPrice()))
                    .peek(childrenPriceChange -> childrenPriceChange.setChangePlanDate(insertParentGoodsPriceChangePlan.getChangePlanDate()))
                    .peek(childrenPriceChange -> childrenPriceChange.setPartnerPriceReflect(false))
                    .peek(childrenPriceChange -> childrenPriceChange.setEstimateCreated(false))
                    .distinct()
                    .collect(Collectors.toList());
            for (MPartnerGoodsPriceChangePlan updatePriceChildrenChangePlan : updatePriceChildrenChangePlanList) {
                this.mPartnerGoodsPriceChangePlanService.update(updatePriceChildrenChangePlan);
                // 見積も更新
                updateEstimate(updatePriceChildrenChangePlan);
            }
        }
        return RepeatStatus.FINISHED;
    }

    void updateEstimate(MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan) throws Exception {
        if (mPartnerGoodsPriceChangePlan.getEstimateNo() == null) {
            return;
        }
        TEstimateDetail tEstimateDetail = this.tEstimateDetailService.getByPK(mPartnerGoodsPriceChangePlan.getEstimateNo(), mPartnerGoodsPriceChangePlan.getEstimateDetailNo());
        tEstimateDetail.setGoodsPrice(mPartnerGoodsPriceChangePlan.getAfterPrice());
        this.tEstimateDetailService.update(tEstimateDetail);
        TEstimate tEstimate = tEstimateDetail.getTEstimate();
        tEstimate.setPriceChangeDate(mPartnerGoodsPriceChangePlan.getChangePlanDate());
        this.tEstimateService.update(tEstimate);
    }

    private MPartnerGoodsPriceChangePlan convertParentGoodsPriceChange(CustomParentGoodsPriceChange vParentGoodsPriceChange) {
        MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan = new MPartnerGoodsPriceChangePlan();
        BeanUtils.copyProperties(vParentGoodsPriceChange, mPartnerGoodsPriceChangePlan);
        // 得意先番号と得意先コードを親得意先のものにする
        mPartnerGoodsPriceChangePlan.setPartnerNo(vParentGoodsPriceChange.getCParentPartnerNo());
        mPartnerGoodsPriceChangePlan.setPartnerCode(vParentGoodsPriceChange.getCParentPartnerCode());
        // 各種フラグをfalse
        mPartnerGoodsPriceChangePlan.setEstimateCreated(false);
        mPartnerGoodsPriceChangePlan.setPartnerPriceReflect(false);
        // 新規登録するためPKをnullにする
        mPartnerGoodsPriceChangePlan.setPartnerGoodsPriceChangePlanNo(null);
        return mPartnerGoodsPriceChangePlan;
    }
}
