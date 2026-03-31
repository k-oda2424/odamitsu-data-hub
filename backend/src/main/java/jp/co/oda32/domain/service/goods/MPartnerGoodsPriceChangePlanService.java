package jp.co.oda32.domain.service.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MPartnerGoodsPriceChangePlan;
import jp.co.oda32.domain.repository.goods.MPartnerGoodsPriceChangePlanRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.MPartnerGoodsPriceChangePlanSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 得意先商品価格変更予定Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2022/10/13
 */
@Service
@EnableAutoConfiguration
@RequiredArgsConstructor
public class MPartnerGoodsPriceChangePlanService extends CustomService {
    MPartnerGoodsPriceChangePlanSpecification mPartnerGoodsPriceChangePlanSpecification = new MPartnerGoodsPriceChangePlanSpecification();
    @NonNull
    private final MPartnerGoodsPriceChangePlanRepository mPartnerGoodsPriceChangePlanRepository;

    public List<MPartnerGoodsPriceChangePlan> findAll() {
        return this.mPartnerGoodsPriceChangePlanRepository.findAll();
    }

    public List<MPartnerGoodsPriceChangePlan> findEstimateNotCreate(Integer shopNo) {
        return this.mPartnerGoodsPriceChangePlanRepository.findAll(Specification
                .where(this.mPartnerGoodsPriceChangePlanSpecification.shopNoContains(shopNo))
                .and(this.mPartnerGoodsPriceChangePlanSpecification.estimateCreatedContains(false))
                .and(this.mPartnerGoodsPriceChangePlanSpecification.delFlgContains(Flag.NO)));
    }

    public List<MPartnerGoodsPriceChangePlan> findParentGoodsPriceChangePlanNotCreated(Integer shopNo, List<Integer> goodsNoList, Integer partnerNo, Integer destinationNo, LocalDate changePlanDate) {
        if (partnerNo != null && partnerNo == 0) {
            partnerNo = null;
        }
        if (destinationNo != null && destinationNo == 0) {
            destinationNo = null;
        }
        return this.mPartnerGoodsPriceChangePlanRepository.findAll(Specification
                .where(this.mPartnerGoodsPriceChangePlanSpecification.shopNoContains(shopNo))
                .and(this.mPartnerGoodsPriceChangePlanSpecification.goodsNoListContains(goodsNoList))
                .and(this.mPartnerGoodsPriceChangePlanSpecification.partnerNoContains(partnerNo))
                .and(this.mPartnerGoodsPriceChangePlanSpecification.destinationNoContains(destinationNo))
                .and(this.mPartnerGoodsPriceChangePlanSpecification.changePlanDateContains(changePlanDate))
                .and(this.mPartnerGoodsPriceChangePlanSpecification.delFlgContains(Flag.NO)));
    }

    public List<MPartnerGoodsPriceChangePlan> findParentGoodsPriceChangePlanNotCreated(List<Integer> partnerNoList) {
        return this.mPartnerGoodsPriceChangePlanRepository.findAll(Specification
                .where(this.mPartnerGoodsPriceChangePlanSpecification.partnerNoListContains(partnerNoList))
                .and(this.mPartnerGoodsPriceChangePlanSpecification.parentChangePlanNoIsNull())
                .and(this.mPartnerGoodsPriceChangePlanSpecification.delFlgContains(Flag.NO)));
    }

    public int updateGoodsNo() {
        return this.mPartnerGoodsPriceChangePlanRepository.updateGoodsNo();
    }

    public MPartnerGoodsPriceChangePlan insert(MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan) throws Exception {
        return this.insert(this.mPartnerGoodsPriceChangePlanRepository, mPartnerGoodsPriceChangePlan);
    }

    public MPartnerGoodsPriceChangePlan update(MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan) throws Exception {
        return this.update(this.mPartnerGoodsPriceChangePlanRepository, mPartnerGoodsPriceChangePlan);
    }

    public List<MPartnerGoodsPriceChangePlan> update(List<MPartnerGoodsPriceChangePlan> mPartnerGoodsPriceChangePlanList) throws Exception {
        List<MPartnerGoodsPriceChangePlan> insertList = new ArrayList<>();
        for (MPartnerGoodsPriceChangePlan mPartnerGoodsPriceChangePlan : mPartnerGoodsPriceChangePlanList) {
            insertList.add(this.update(mPartnerGoodsPriceChangePlan));
        }
        return insertList;
    }
}
