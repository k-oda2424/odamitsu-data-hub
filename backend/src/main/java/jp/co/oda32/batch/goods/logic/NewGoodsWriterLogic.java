package jp.co.oda32.batch.goods.logic;

import jp.co.oda32.constant.TaxCategory;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MGoodsUnitService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MMakerService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.util.BigDecimalUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 新規取込商品における商品取り込み処理
 *
 * @author k_oda
 * @since 2018/07/20
 */
@Service
public class NewGoodsWriterLogic extends AbstractGoodsFileWriterLogic {

    @Autowired
    public NewGoodsWriterLogic(MGoodsService goodsService, MGoodsUnitService goodsUnitService, MSupplierService supplierService, MMakerService makerService, WSalesGoodsService wSalesGoodsService) {
        super(goodsService, goodsUnitService, supplierService, makerService, wSalesGoodsService);
    }

    /**
     * 登録処理
     */
    @Override
    public void register() throws Exception {
        this.saveMaker();
        this.saveMGoods();
        // 入り数を商品単位マスタに変換して登録
        this.saveMGoodsUnit();
        // 販売商品ワーク
        this.saveSalesGoods();

    }


    /**
     * 商品マスタを設定
     */
    @Override
    public void saveMGoods() throws Exception {
        TaxCategory taxCategory;
        if (BigDecimalUtil.isEqual(BigDecimal.ONE, this.goodsFile.get非課税区分())) {
            // 非課税の場合1になっている
            taxCategory = TaxCategory.EXEMPT;
        } else {
            // 0：通常　1：軽減税率
            taxCategory = TaxCategory.purse(this.goodsFile.get新税分類());
        }
        MGoods goods = MGoods.builder()
                .goodsName(this.goodsFile.get商品名())
                .keyword(this.goodsFile.get商品名索引())
                .janCode(this.goodsFile.getＪＡＮ())
                .taxCategory(taxCategory.getCode())
                .caseContainNum(this.goodsFile.get入数())
                .smileGoodsName(this.goodsFile.get商品名())
                .build();
        if (!StringUtil.isEmpty(this.goodsFile.getメーカー名())) {
            // メーカー名が空でなければメーカー番号を設定する
            goods.setMakerNo(this.existMakerMap.get(this.goodsFile.getメーカー名()).getMakerNo());
        }
        // 商品マスタEntityに設定
        this.mGoods = this.goodsService.insert(goods);
    }

    /**
     * 販売商品系マスタを登録、更新します。
     */
    @Override
    public void saveSalesGoods() throws Exception {
        WSalesGoods salesGoods = WSalesGoods.builder()
                .goodsCode(this.goodsFile.get商品コード())
                .goodsNo(this.mGoods.getGoodsNo())
                .keyword(this.mGoods.getKeyword())
                .shopNo(this.shopNo)
                .goodsName(this.mGoods.getGoodsName())
                .purchasePrice(this.goodsFile.get標準仕入単価())
                .goodsPrice(this.goodsFile.get標準売上単価())
                .build();
        this.wSalesGoodsService.insert(salesGoods);
    }
}
