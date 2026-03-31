package jp.co.oda32.batch.goods.logic;

/**
 * 商品取込processor処理のインターフェースクラス
 *
 * @author k_oda
 * @since 2018/07/20
 */
public interface IGoodsFileWriterLogic {

    /**
     * 登録処理
     */
    void register() throws Exception;

    /**
     * メーカーマスタを登録、更新します。
     */
    void saveMaker() throws Exception;


    /**
     * 商品マスタを登録、更新します。
     */
    void saveMGoods() throws Exception;

    /**
     * 販売商品系マスタを登録、更新します。
     */
    void saveSalesGoods() throws Exception;

    /**
     * 商品単位マスタを登録、更新します。
     */
    void saveMGoodsUnit() throws Exception;
}
