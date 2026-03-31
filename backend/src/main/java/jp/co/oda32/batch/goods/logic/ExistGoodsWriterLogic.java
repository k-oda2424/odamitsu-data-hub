package jp.co.oda32.batch.goods.logic;

import jp.co.oda32.constant.TaxCategory;
import jp.co.oda32.domain.model.goods.MGoodsUnit;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MMaker;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MGoodsUnitService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MMakerService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.util.BigDecimalUtil;
import jp.co.oda32.util.StringUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 既存商品に対する商品ファイル取込処理
 *
 * @author k_oda
 * @since 2018/07/20
 */
@Log4j2
@Service
public class ExistGoodsWriterLogic extends AbstractGoodsFileWriterLogic {
    @Autowired
    public ExistGoodsWriterLogic(MGoodsService goodsService, MGoodsUnitService goodsUnitService, MSupplierService supplierService, MMakerService makerService, WSalesGoodsService wSalesGoodsService) {
        super(goodsService, goodsUnitService, supplierService, makerService, wSalesGoodsService);
    }

    /**
     * 登録処理
     */
    @Override
    public void register() throws Exception {
        saveMaker();
        // 商品
        saveMGoods();
        // 入り数
        saveMGoodsUnit();
        // 販売商品
        saveSalesGoods();
    }

    /**
     * 商品マスタを設定
     */
    @Override
    public void saveMGoods() throws Exception {
        boolean isUpdate = false;
        if (StringUtil.isEmpty(this.mGoods.getKeyword()) && !StringUtil.isEmpty(this.goodsFile.get商品名索引())) {
            this.mGoods.setKeyword(String.format("%s", this.goodsFile.get商品名索引()));
            isUpdate = true;
        } else if (!StringUtil.isEmpty(this.goodsFile.get商品名索引()) && Arrays.stream(this.mGoods.getKeyword().split(",")).noneMatch(keyword -> keyword.equals(this.goodsFile.get商品名索引()))) {
            // キーワード追加
            this.mGoods.setKeyword(String.format("%s,%s", this.mGoods.getKeyword(), this.goodsFile.get商品名索引()));
            isUpdate = true;
        }
        // 商品名変更チェック
        if (!this.mGoods.getGoodsName().equals(this.goodsFile.get商品名())) {
            // 商品名変更
            log.warn(String.format("商品名更新。商品番号:%d,old:%s,new:%s", this.mGoods.getGoodsNo(), this.mGoods.getGoodsName(), this.goodsFile.get商品名()));
            this.mGoods.setGoodsName(this.goodsFile.get商品名());
            isUpdate = true;
        }
        // SMILE商品名変更チェック
        if (StringUtil.isEmpty(this.mGoods.getSmileGoodsName()) || !this.mGoods.getSmileGoodsName().equals(this.goodsFile.get商品名())) {
            log.warn(String.format("SMILE商品名更新。商品番号:%d,old:%s,new:%s", this.mGoods.getGoodsNo(), this.mGoods.getSmileGoodsName(), this.goodsFile.get商品名()));
            this.mGoods.setSmileGoodsName(this.goodsFile.get商品名());
            isUpdate = true;
        }
        if (this.goodsFile.getＪＡＮ() != null && !StringUtil.isEqual(this.mGoods.getJanCode(), this.goodsFile.getＪＡＮ())) {
            log.warn(String.format("JANコード更新。getＪＡＮ:%d,old:%s,new:%s", this.mGoods.getGoodsNo(), this.mGoods.getJanCode(), this.goodsFile.getＪＡＮ()));
            this.mGoods.setJanCode(this.goodsFile.getＪＡＮ());
            isUpdate = true;
        }
        if (!StringUtil.isEmpty(this.goodsFile.getメーカー名())) {
            MMaker mMaker = this.existMakerMap.get(this.goodsFile.getメーカー名());
            if (mMaker != null && (this.mGoods.getMakerNo() == null || !this.mGoods.getMakerNo().equals(mMaker.getMakerNo()))) {
                // メーカー番号更新
                log.warn(String.format("メーカー番号更新。商品番号:%d,old:%s,new:%s", this.mGoods.getGoodsNo(), this.mGoods.getMakerNo(), mMaker.getMakerNo()));
                this.mGoods.setMakerNo(mMaker.getMakerNo());
                isUpdate = true;
            }
        }
        if (!BigDecimalUtil.isEqual(this.mGoods.getCaseContainNum(), this.goodsFile.get入数())) {
            log.warn(String.format("ケース入数更新。商品番号:%d,old:%s,new:%s", this.mGoods.getGoodsNo(), this.mGoods.getCaseContainNum(), this.goodsFile.get入数()));
            this.mGoods.setCaseContainNum(this.goodsFile.get入数());
            isUpdate = true;
        }
        TaxCategory taxCategory;
        if (BigDecimalUtil.isEqual(BigDecimal.ONE, this.goodsFile.get非課税区分())) {
            // 非課税の場合1になっている
            taxCategory = TaxCategory.EXEMPT;
        } else {
            // 0：通常　1：軽減税率
            taxCategory = TaxCategory.purse(this.goodsFile.get新税分類());
        }
        if (this.mGoods.getTaxCategory() == null || this.mGoods.getTaxCategory() != taxCategory.getCode()) {
            log.info(String.format("消費税率区分更新 1:通常 2:軽減税率 更新前：%s 更新後：%s", this.mGoods.getTaxCategory(), this.goodsFile.get消費税率区分().toString()));
            this.mGoods.setTaxCategory(taxCategory.getCode());
            isUpdate = true;
        }
        if (isUpdate) {
            // 更新
            this.goodsService.update(this.mGoods);
        }
    }

    /**
     * 販売商品系マスタを登録、更新します。
     */
    @Override
    public void saveSalesGoods() throws Exception {
        WSalesGoods wSalesGoods = new WSalesGoods();
        // iSalesGoodsをコピー
        BeanUtils.copyProperties(this.iSalesGoods, wSalesGoods);
        if (!BigDecimalUtil.isEqual(this.iSalesGoods.getPurchasePrice(), this.goodsFile.get標準仕入単価())) {
            log.warn(String.format("仕入単価が変更されています。ショップ番号：%d 商品コード：%s old仕入単価:%s new仕入単価：%s"
                    , this.shopNo, this.iSalesGoods.getGoodsCode(), this.iSalesGoods.getPurchasePrice(), this.goodsFile.get標準仕入単価()));
            wSalesGoods.setPurchasePrice(this.goodsFile.get標準仕入単価());
        }
        if (!BigDecimalUtil.isEqual(this.iSalesGoods.getGoodsPrice(), this.goodsFile.get標準売上単価())) {
            log.warn(String.format("売単価が変更されています。ショップ番号：%d 商品コード：%s old売単価:%s new売単価：%s"
                    , this.shopNo, this.iSalesGoods.getGoodsCode(), this.iSalesGoods.getGoodsPrice(), this.goodsFile.get標準売上単価()));
            wSalesGoods.setGoodsPrice(this.goodsFile.get標準売上単価());
        }
        // 商品名変更チェック
        if (!wSalesGoods.getGoodsName().equals(this.goodsFile.get商品名())) {
            // 商品名変更
            log.warn(String.format("商品名更新。ショップ番号：%d 商品コード：%s ,old:%s,new:%s", this.shopNo, this.iSalesGoods.getGoodsCode(), this.mGoods.getGoodsName(), this.goodsFile.get商品名()));
            wSalesGoods.setGoodsName(this.goodsFile.get商品名());
        }
        if (!this.iSalesGoods.getIsWork()) {
            // 販売商品ワークテーブルの登録なし
            this.wSalesGoodsService.insert(wSalesGoods);
            return;
        }
        // 販売商品ワークテーブル登録あり
        this.wSalesGoodsService.update(wSalesGoods);
    }

    /**
     * 商品単位マスタを設定
     */
    @Override
    public void saveMGoodsUnit() throws Exception {
        // 入り数変更チェック
        List<MGoodsUnit> goodsUnitList = this.goodsUnitService.findByGoodsNo(this.mGoods.getGoodsNo());
        Optional<MGoodsUnit> goodsUnitOpt = goodsUnitList.stream()
                .filter(unit -> unit.getUnit().equals(this.UNIT.getValue()))
                .findFirst();
        if (!goodsUnitOpt.isPresent()) {
            // 入り数登録がないので新規登録
            super.saveMGoodsUnit();
            return;
        }
        // 入り数更新確認
        MGoodsUnit mGoodsUnit = goodsUnitOpt.get();
        if (BigDecimalUtil.isEqual(mGoodsUnit.getContainNum(), this.goodsFile.get入数())) {
            // 同じだったら更新しない
            return;
        }
        log.warn(String.format("商品単位更新。商品単位番号:%d,入り数old：%s,new：%s", mGoodsUnit.getUnitNo(), mGoodsUnit.getContainNum(), this.goodsFile.get入数().intValue()));
        mGoodsUnit.setContainNum(this.goodsFile.get入数());
        this.goodsUnitService.update(mGoodsUnit);
    }

}
