package jp.co.oda32.domain.service.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.UnitType;
import jp.co.oda32.domain.model.goods.ISalesGoods;
import jp.co.oda32.domain.model.goods.MGoodsUnit;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.service.purchase.MPurchasePriceService;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 販売商品系共通サービスクラス
 *
 * @author k_oda
 * @since 2019/07/15
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class CommonSalesGoodsService {

    @NonNull
    private MSalesGoodsService mSalesGoodsService;
    @NonNull
    private WSalesGoodsService wSalesGoodsService;
    @NonNull
    private MGoodsUnitService mGoodsUnitService;
    @NonNull
    private MPurchasePriceService mPurchasePriceService;

    public ISalesGoods getExistSalesGoodsByUniqKey(String goodsCode, Integer shopNo) {
        WSalesGoods wSalesGoods = this.wSalesGoodsService.getByShopNoAndGoodsCode(shopNo, goodsCode);
        if (wSalesGoods != null) {
            return wSalesGoods;
        }
        return this.mSalesGoodsService.getByShopNoAndGoodsCode(shopNo, goodsCode);
    }

    public ISalesGoods getExistSalesGoodsByPK(Integer goodsNo, Integer shopNo) {
        WSalesGoods wSalesGoods = this.wSalesGoodsService.getByPK(shopNo, goodsNo);
        if (wSalesGoods != null) {
            return wSalesGoods;
        }
        return this.mSalesGoodsService.getByPK(shopNo, goodsNo);
    }

    public List<ISalesGoods> findExistSalesGoods(List<String> goodsCodeList, Integer shopNo) {
        List<WSalesGoods> wSalesGoodsList = this.wSalesGoodsService.findByShopNoAndGoodsCode(shopNo, goodsCodeList);
        List<ISalesGoods> iSalesGoodsList = wSalesGoodsList.stream().map(wSalesGoods -> (ISalesGoods) wSalesGoods).collect(Collectors.toList());
        List<String> existGoodsCodeList = iSalesGoodsList.stream().map(ISalesGoods::getGoodsCode).collect(Collectors.toList());
        if (new HashSet<>(existGoodsCodeList).containsAll(goodsCodeList)) {
            return iSalesGoodsList;
        }
        List<String> reFindGoodsCodeList = goodsCodeList.stream().filter(goodsCode -> !existGoodsCodeList.contains(goodsCode)).collect(Collectors.toList());
        List<MSalesGoods> mSalesGoodsList = this.mSalesGoodsService.findByShopNoAndGoodsCode(shopNo, reFindGoodsCodeList);
        List<ISalesGoods> mSalesGoodsFoundList = mSalesGoodsList.stream().map(wSalesGoods -> (ISalesGoods) wSalesGoods).collect(Collectors.toList());
        iSalesGoodsList.addAll(mSalesGoodsFoundList);
        return iSalesGoodsList;
    }

    /**
     * ショップ番号から販売商品系テーブルを検索して返します。
     * 販売商品ワークと販売商品を両方検索するのでフロント画面(販売)用では使用しないこと
     *
     * @param shopNo ショップ番号
     * @return ショップに関連するすべての販売商品
     */
    public List<ISalesGoods> findSalesGoodsByShopNo(Integer shopNo) {
        List<WSalesGoods> wSalesGoodsList = this.wSalesGoodsService.findByShopNo(shopNo);
        List<ISalesGoods> iSalesGoodsList = wSalesGoodsList.stream().map(wSalesGoods -> (ISalesGoods) wSalesGoods).collect(Collectors.toList());
        List<MSalesGoods> mSalesGoodsList = this.mSalesGoodsService.findByShopNo(shopNo);
        List<ISalesGoods> mSalesGoodsFoundList = mSalesGoodsList.stream().map(wSalesGoods -> (ISalesGoods) wSalesGoods).collect(Collectors.toList());
        List<ISalesGoods> nonMatchGoodsList = mSalesGoodsFoundList.stream().filter(salesGoods -> iSalesGoodsList.stream().noneMatch(wSalesGoods -> wSalesGoods.getGoodsCode().equals(salesGoods.getGoodsCode()))).collect(Collectors.toList());
        iSalesGoodsList.addAll(nonMatchGoodsList);
        return iSalesGoodsList;
    }

    public List<ISalesGoods> findSalesGoods(Integer shopNo, String goodsCode, String goodsName, Integer supplierNo) {
        List<WSalesGoods> wSalesGoodsList = this.wSalesGoodsService.find(shopNo, null, goodsName, null, goodsCode, null, supplierNo, Flag.NO);
        List<ISalesGoods> iSalesGoodsList = wSalesGoodsList.stream().map(wSalesGoods -> (ISalesGoods) wSalesGoods).collect(Collectors.toList());
        List<MSalesGoods> mSalesGoodsList = this.mSalesGoodsService.find(shopNo, null, goodsName, goodsCode, null, supplierNo, Flag.NO);
        List<ISalesGoods> mSalesGoodsFoundList = mSalesGoodsList.stream().map(wSalesGoods -> (ISalesGoods) wSalesGoods).collect(Collectors.toList());
        List<ISalesGoods> nonMatchGoodsList = mSalesGoodsFoundList.stream()
                .filter(salesGoods -> iSalesGoodsList.stream().noneMatch(wSalesGoods -> wSalesGoods.getGoodsCode().equals(salesGoods.getGoodsCode())))
                .collect(Collectors.toList());
        iSalesGoodsList.addAll(nonMatchGoodsList);
        return iSalesGoodsList;
    }

    /**
     * 直近仕入価格を設定した販売商品Entityを取得します
     *
     * @param shopNo     ショップ番号
     * @param goodsNo    商品番号
     * @param goodsCode  商品コード
     * @param goodsName  商品名
     * @param supplierNo 仕入先番号
     * @return 販売商品Entity
     */
    public List<ISalesGoods> findPurchaseGoods(Integer shopNo, Integer goodsNo, String goodsCode, String goodsName, Integer supplierNo) {
        List<MPurchasePrice> mPurchasePriceList = this.mPurchasePriceService.find(shopNo, goodsNo, goodsCode, goodsName, null, supplierNo, 0, Flag.NO);
        return mPurchasePriceList.stream().map(this::convertMPurchasePriceToISalesGoods).collect(Collectors.toList());
    }

    private ISalesGoods convertMPurchasePriceToISalesGoods(MPurchasePrice mPurchasePrice) {
        ISalesGoods goods = mPurchasePrice.getWSalesGoods();
        goods.setPurchasePrice(mPurchasePrice.getGoodsPrice());
        return goods;
    }

    /**
     * ケースの入数を返します。
     *
     * @param goodsNo 商品番号
     * @return ケースの入数
     */
    public BigDecimal getCaseGoodsUnitNum(Integer goodsNo) {
        return this.mGoodsUnitService.findByGoodsNo(goodsNo)
                .stream().filter(goodsUnit -> StringUtil.isEqual(goodsUnit.getUnit(), UnitType.CASE.getValue()))
                .map(MGoodsUnit::getContainNum)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    /**
     * 引数の商品番号リストに対する商品単位を取得して返します。
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品単位リスト
     */
    public List<MGoodsUnit> findCaseGoodsUnit(List<Integer> goodsNoList) {
        return this.mGoodsUnitService.findByGoodsNoList(goodsNoList)
                .stream().filter(goodsUnit -> StringUtil.isEqual(goodsUnit.getUnit(), UnitType.CASE.getValue()))
                .collect(Collectors.toList());
    }
}
