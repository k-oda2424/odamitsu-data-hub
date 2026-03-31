package jp.co.oda32.batch.goods.logic;

import jp.co.oda32.constant.TaxCategory;
import jp.co.oda32.constant.TaxType;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.smile.ISmileGoodsFile;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

/**
 * 販売商品を新規登録するクラス
 * 仕入、注文登録時を想定
 *
 * @author k_oda
 * @since 2019/10/10
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SalesGoodsCreateService {
    @NonNull
    MGoodsService mGoodsService;
    @NonNull
    WSalesGoodsService wSalesGoodsService;

    public WSalesGoods goodsProcess(ISmileGoodsFile iSmileGoodsFile) throws Exception {
        log.info(String.format("商品を新規登録します。商品名:%s", iSmileGoodsFile.getShouhinMei()));

        // 商品コードが空の場合は登録をスキップ
        String goodsCode = iSmileGoodsFile.getShouhinCode();
        if (StringUtil.isEmpty(goodsCode)) {
            log.warn(String.format("商品コードが空のため、商品「%s」の登録をスキップします。", iSmileGoodsFile.getShouhinMei()));
            return null;
        }

        TaxCategory taxCategory;
        if (StringUtil.isEqual(TaxType.TAX_FREE.getValue(), iSmileGoodsFile.getKazeiKubun())) {
            // 非課税の場合消費税分類：0だが、課税区分：2になっている
            taxCategory = TaxCategory.EXEMPT;
        } else {
            // 0：通常　1：軽減税率
            taxCategory = TaxCategory.purse(iSmileGoodsFile.getShouhizeiBunrui());
        }
        MGoods goods = MGoods.builder()
                .goodsName(iSmileGoodsFile.getShouhinMei())
                .smileGoodsName(iSmileGoodsFile.getShouhinMei())
                .taxCategory(taxCategory.getCode())
                .taxCategoryName(taxCategory.getDescription())
                .caseContainNum(iSmileGoodsFile.getIrisu())
                .build();

        goods = this.mGoodsService.insert(goods);

        WSalesGoods wSalesGoods = WSalesGoods.builder()
                .goodsNo(goods.getGoodsNo())
                .goodsCode(goodsCode)
                .goodsName(iSmileGoodsFile.getShouhinMei())
                .shopNo(iSmileGoodsFile.getShopNo())
                .purchasePrice(iSmileGoodsFile.getTanka())
                .build();

        wSalesGoods = this.wSalesGoodsService.insert(wSalesGoods);
        return wSalesGoods;
    }
}
