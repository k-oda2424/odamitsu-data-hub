package jp.co.oda32.batch.order.logic;

/**
 * SMILE注文取込processor処理のインターフェースクラス
 *
 * @author k_oda
 * @since 2018/07/20
 */
public interface ISmileOrderFileWriterLogic {

    /**
     * 取引先処理
     */
    void partnerProcess() throws Exception;

    /**
     * 登録処理
     */
    void register() throws Exception;
}
