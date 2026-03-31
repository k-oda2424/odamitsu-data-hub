package jp.co.oda32.batch.goods.logic;

import jp.co.oda32.batch.goods.GoodsFile;
import jp.co.oda32.domain.model.goods.ISalesGoods;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.service.goods.MSalesGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

/**
 * 商品ファイル取込Writer処理のファクトリークラス
 *
 * @author k_oda
 * @since 2018/07/20
 */
@Service
@Scope("step")
@RequiredArgsConstructor
public class GoodsFileWriterFactory {
    @NonNull
    private MSalesGoodsService mSalesGoodsService;
    @NonNull
    private WSalesGoodsService wSalesGoodsService;
    @NonNull
    private ExistGoodsWriterLogic existGoodsWriterLogic;
    @NonNull
    private NewGoodsWriterLogic newGoodsWriterLogic;
    @Value("#{jobParameters['shopNo']}")
    private Integer shopNo;


    public IGoodsFileWriterLogic getGoodsFileProcessorLogic(GoodsFile item) throws Exception {
        // 既存商品であるか検索
        // 販売商品系テーブルを商品コードで検索
        ISalesGoods existSalesGoods = this.getExistWSalesGoods(item.get商品コード());
        if (existSalesGoods != null) {
            // 既存商品の場合
            this.existGoodsWriterLogic.setGoodsData(this.shopNo, item, existSalesGoods);
            return this.existGoodsWriterLogic;
        }
        // 新規商品の場合
        this.newGoodsWriterLogic.setGoodsData(this.shopNo, item, null);
        return this.newGoodsWriterLogic;
    }

    /**
     * バッチ起動時のshopNoと商品コードから存在する販売商品を取得し、商品マスタEntityを返します。
     *
     * @param goodsCode 商品コード
     * @return 存在する商品Entity
     * @throws Exception 複数の販売商品が存在した場合
     */
    private ISalesGoods getExistWSalesGoods(String goodsCode) throws Exception {
        WSalesGoods wSalesGoods = this.wSalesGoodsService.getByShopNoAndGoodsCode(this.shopNo, goodsCode);
        if (wSalesGoods != null) {
            return wSalesGoods;
        }
        MSalesGoods mSalesGoods = this.mSalesGoodsService.getByShopNoAndGoodsCode(this.shopNo, goodsCode);
        if (mSalesGoods != null) {
            return mSalesGoods;
        }
        return null;
    }
}
