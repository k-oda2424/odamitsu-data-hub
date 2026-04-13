package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.annotation.SkipShopCheck;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.repository.purchase.MPurchasePriceChangePlanRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.purchase.MPurchasePriceChangePlanSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 仕入価格変更予定Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2022/10/13
 */
@Service
@EnableAutoConfiguration
@RequiredArgsConstructor
@Log4j2
public class MPurchasePriceChangePlanService extends CustomService {
    @NonNull
    private final MPurchasePriceChangePlanRepository mPurchasePriceChangePlanRepository;

    private MPurchasePriceChangePlanSpecification mPurchasePriceChangePlanSpecification = new MPurchasePriceChangePlanSpecification();

    public List<MPurchasePriceChangePlan> find(Integer shopNo, String supplierCode, String goodsCode, String janCode, String purchasePriceChangeReason, LocalDate priceChangeDateFrom, LocalDate priceChangeDateTo, Flag delFlg) {
        return this.mPurchasePriceChangePlanRepository.findAll(buildFindSpec(shopNo, supplierCode, goodsCode, janCode, purchasePriceChangeReason, priceChangeDateFrom, priceChangeDateTo, delFlg));
    }

    @SkipShopCheck
    public Page<MPurchasePriceChangePlan> findPaged(Integer shopNo, String supplierCode, String goodsCode, String janCode, String purchasePriceChangeReason, LocalDate priceChangeDateFrom, LocalDate priceChangeDateTo, Flag delFlg, String scope, Pageable pageable) {
        Specification<MPurchasePriceChangePlan> spec = buildFindSpec(shopNo, supplierCode, goodsCode, janCode, purchasePriceChangeReason, priceChangeDateFrom, priceChangeDateTo, delFlg)
                .and(this.mPurchasePriceChangePlanSpecification.scopeFilter(scope));
        return this.mPurchasePriceChangePlanRepository.findAll(spec, pageable);
    }

    private Specification<MPurchasePriceChangePlan> buildFindSpec(Integer shopNo, String supplierCode, String goodsCode, String janCode, String purchasePriceChangeReason, LocalDate priceChangeDateFrom, LocalDate priceChangeDateTo, Flag delFlg) {
        return Specification
                .where(this.mPurchasePriceChangePlanSpecification.shopNoContains(shopNo))
                .and(this.mPurchasePriceChangePlanSpecification.supplierCodeContains(supplierCode))
                .and(this.mPurchasePriceChangePlanSpecification.goodsCodeContains(goodsCode))
                .and(this.mPurchasePriceChangePlanSpecification.janCodeContains(janCode))
                .and(this.mPurchasePriceChangePlanSpecification.changeReasonContains(purchasePriceChangeReason))
                .and(this.mPurchasePriceChangePlanSpecification.changePlanDateRangeContains(priceChangeDateFrom, priceChangeDateTo))
                .and(this.mPurchasePriceChangePlanSpecification.delFlgContains(delFlg));
    }

    public List<MPurchasePriceChangePlan> find(Integer shopNo, String supplierCode, String goodsCode, Integer partnerNo, Integer destinationNo, LocalDate priceChangeDate, Flag delFlg) {
        return this.mPurchasePriceChangePlanRepository.findAll(Specification
                .where(this.mPurchasePriceChangePlanSpecification.shopNoContains(shopNo))
                .and(this.mPurchasePriceChangePlanSpecification.supplierCodeContains(supplierCode))
                .and(this.mPurchasePriceChangePlanSpecification.goodsCodeContains(goodsCode))
                .and(this.mPurchasePriceChangePlanSpecification.partnerNoContains(partnerNo))
                .and(this.mPurchasePriceChangePlanSpecification.destinationNoContains(destinationNo))
                .and(this.mPurchasePriceChangePlanSpecification.changePlanDateContains(priceChangeDate))
                .and(this.mPurchasePriceChangePlanSpecification.delFlgContains(delFlg)));
    }

    public MPurchasePriceChangePlan getLatest(Integer shopNo, String supplierCode, String goodsCode, String janCode) throws Exception {
        List<MPurchasePriceChangePlan> list = this.find(shopNo, supplierCode, goodsCode, janCode, null, null, null, Flag.NO);
        if (list.isEmpty()) {
            return null;
        }
        if (list.stream().map(MPurchasePriceChangePlan::getGoodsCode).distinct().count() > 1) {
            throw new Exception("複数商品が該当します。検索条件を確認してください。");
        }
        return list.stream()
                .sorted(Comparator.comparing(MPurchasePriceChangePlan::getChangePlanDate).reversed())
                .findFirst().orElse(null);
    }

    /**
     * 得意先価格変更予定を作成していないリストを返します
     *
     * @return 得意先価格変更予定を作成していないリスト
     */
    public List<MPurchasePriceChangePlan> findPartnerPriceChangePlanNotCreate(Integer shopNo) {
        return this.mPurchasePriceChangePlanRepository.findAll(Specification
                .where(this.mPurchasePriceChangePlanSpecification.shopNoContains(shopNo))
                .and(this.mPurchasePriceChangePlanSpecification.partnerPriceChangePlanCreatedContains(false))
                .and(this.mPurchasePriceChangePlanSpecification.delFlgContains(Flag.NO)));
    }

    public List<MPurchasePriceChangePlan> findByPurchasePriceReflectFalse() {
        return this.mPurchasePriceChangePlanRepository.findByPurchasePriceReflectFalse();
    }

    /**
     * 仕入価格を登録します。
     *
     * @param purchasePriceChangePlan 仕入価格Entity
     * @return 登録した仕入価格Entity
     */
    public MPurchasePriceChangePlan insert(MPurchasePriceChangePlan purchasePriceChangePlan) throws Exception {
        return this.insert(this.mPurchasePriceChangePlanRepository, purchasePriceChangePlan);
    }

    /**
     * 仕入価格を一括登録します。途中で例外が発生した場合は全件ロールバック。
     */
    @org.springframework.transaction.annotation.Transactional
    public List<MPurchasePriceChangePlan> bulkInsert(List<MPurchasePriceChangePlan> plans) throws Exception {
        List<MPurchasePriceChangePlan> result = new java.util.ArrayList<>();
        for (MPurchasePriceChangePlan plan : plans) {
            result.add(this.insert(plan));
        }
        return result;
    }

    /**
     * 仕入価格を更新します。
     *
     * @param purchasePriceChangePlan 仕入価格Entity
     * @return 更新した仕入価格Entity
     */
    public MPurchasePriceChangePlan update(MPurchasePriceChangePlan purchasePriceChangePlan) throws Exception {
        return this.update(this.mPurchasePriceChangePlanRepository, purchasePriceChangePlan);
    }

    /**
     * 仕入価格マスタ反映フラグを立てます。
     * 仕入価格が反映されている仕入予定価格が対象です。
     */
    public void updateReflectComplete() {
        log.info(String.format("仕入価格反映フラグ更新：%d件", this.mPurchasePriceChangePlanRepository.updateReflectComplete()));
    }

    /**
     * 商品名で仕入価格変更予定を検索します（ポップアップ商品検索用）。
     */
    public List<MPurchasePriceChangePlan> findByGoodsName(Integer shopNo, String goodsName) {
        return this.mPurchasePriceChangePlanRepository.findAll(Specification
                .where(this.mPurchasePriceChangePlanSpecification.shopNoContains(shopNo))
                .and(this.mPurchasePriceChangePlanSpecification.goodsNameContains(goodsName))
                .and(this.mPurchasePriceChangePlanSpecification.delFlgContains(Flag.NO)));
    }

    public MPurchasePriceChangePlan getById(Integer id) {
        return this.mPurchasePriceChangePlanRepository.findById(id).orElse(null);
    }

    public void deleteByGoodsCodeAndChangePlanDate(Integer shopNo, String goodsCode, LocalDate changePlanDate) {
        this.mPurchasePriceChangePlanRepository.deleteByShopNoAndGoodsCodeAndChangePlanDate(shopNo, goodsCode, changePlanDate);
    }
}
