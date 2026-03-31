package jp.co.oda32.batch.goods.logic;

import jp.co.oda32.batch.goods.GoodsFile;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.UnitType;
import jp.co.oda32.domain.model.goods.ISalesGoods;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MGoodsUnit;
import jp.co.oda32.domain.model.master.MMaker;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MGoodsUnitService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MMakerService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author k_oda
 * @since 2018/07/20
 */
@Service
@RequiredArgsConstructor
public abstract class AbstractGoodsFileWriterLogic implements IGoodsFileWriterLogic {
    @NonNull
    MGoodsService goodsService;
    @NonNull
    MGoodsUnitService goodsUnitService;
    @NonNull
    MSupplierService supplierService;
    @NonNull
    MMakerService makerService;
    @NonNull
    WSalesGoodsService wSalesGoodsService;
    protected Integer shopNo;
    protected GoodsFile goodsFile;
    protected ISalesGoods iSalesGoods;
    protected MGoods mGoods;
    // このバッチでの入り数はケース単位と固定とする
    protected final UnitType UNIT = UnitType.CASE;

    // メーカー検索を減らすためのキャッシュ
    protected Map<String, MMaker> existMakerMap = new HashMap<>();

    /**
     * 商品情報を設定します。
     *
     * @param shopNo      登録更新処理を行うショップ番号
     * @param goodsFile   商品ファイル
     * @param iSalesGoods 販売商品ワークEntity
     */
    public void setGoodsData(Integer shopNo, GoodsFile goodsFile, ISalesGoods iSalesGoods) {
        this.shopNo = shopNo;
        this.goodsFile = goodsFile;
        if (iSalesGoods != null) {
            this.iSalesGoods = iSalesGoods;
            this.mGoods = iSalesGoods.getMGoods();
        }
    }

    /**
     * メーカーマスタを登録、更新します。
     */
    @Override
    public void saveMaker() throws Exception {
        String makerName = this.goodsFile.getメーカー名();
        if (StringUtil.isEmpty(makerName)) {
            // メーカー名なし
            return;
        }
        MMaker existMaker = getExistMaker(makerName);
        if (existMaker != null) {
            // 名前も同じ、削除フラグがたっている場合、戻す。
            if (Flag.YES.getValue().equals(existMaker.getDelFlg())) {
                existMaker.setDelFlg(Flag.NO.getValue());
                this.makerService.update(existMaker);
            }
            return;
        }
        // 存在しないので登録
        MMaker newMaker = MMaker.builder()
                .makerName(makerName)
                .build();
        MMaker maker = this.makerService.insert(newMaker);
        this.existMakerMap.put(makerName, maker);
    }

    /**
     * メーカーマスタをメーカー名で検索して存在するメーカーマスタEntityを返します。
     *
     * @param makerName メーカー名
     * @return 存在するメーカーマスタEntity 存在しない場合、nullを返します。
     */
    private MMaker getExistMaker(String makerName) {
        if (this.existMakerMap.containsKey(makerName)) {
            return this.existMakerMap.get(makerName);
        }
        List<MMaker> makerList = this.makerService.findByMakerName(makerName);
        Optional<MMaker> makerOptional = makerList.stream()
                .findFirst();
        if (makerOptional.isPresent()) {
            MMaker existMaker = makerOptional.get();
            this.existMakerMap.put(makerName, existMaker);
            return existMaker;
        }
        return null;
    }

    /**
     * 商品単位マスタを設定
     */
    @Override
    public void saveMGoodsUnit() throws Exception {
        MGoodsUnit goodsUnit = MGoodsUnit.builder()
                .goodsNo(this.mGoods.getGoodsNo())
                .unit(this.UNIT.getValue())
                .containNum(this.goodsFile.get入数())
                .build();
        goodsUnit = this.goodsUnitService.insert(goodsUnit);
        MGoodsUnit unitSeparate = MGoodsUnit.builder()
                .goodsNo(this.mGoods.getGoodsNo())
                .unit(UnitType.PIECE.getValue())
                .containNum(BigDecimal.ONE)
                .parentUnitNo(goodsUnit.getUnitNo())
                .build();
        this.goodsUnitService.insert(unitSeparate);
    }
}
