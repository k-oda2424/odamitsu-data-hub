package jp.co.oda32.batch.smile;

import jp.co.oda32.constant.OfficeCode;
import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.constant.SmileMeisaiKubun;
import jp.co.oda32.util.StringUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SMILE注文ファイル取込バッチProcessorクラス
 * ファイルの加工クラス
 *
 * @author k_oda
 * @since 2018/11/20
 */
@Component
@Log4j2
public class SmileOrderFileProcessor implements ItemProcessor<SmileOrderFile, SmileOrderFile> {

    private Map<String, Integer> slipNoDetailCountMap = new ConcurrentHashMap<>();

    /**
     * Process the provided item, returning a potentially modified or new item for continued
     * processing.  If the returned result is null, it is assumed that processing of the item
     * should not continue.
     *
     * @param item to be processed
     * @return potentially modified or new item for continued processing, null if processing of the
     * provided item should not continue.
     */
    @Override
    public SmileOrderFile process(SmileOrderFile item) {
        item.setShopNo(getShopNoFromItem(item));
        if ("999999".equals(item.get得意先コード())) {
            // 得意先コード999999は手打ち得意先なので得意先名1を16進数文字列でMD5値を取得する
            String hexString = DigestUtils.md5DigestAsHex(item.get得意先名1().getBytes());
            item.set得意先コード(hexString);
        }
        if (OfficeShopNo.DAINI.getValue() == item.getShopNo() && StringUtil.isEqual(item.get得意先コード(), "910005")) {
            // 社内での売掛処理なのでスキップ
            return null;
        }
        if (SmileMeisaiKubun.TAX.getValue().equals(item.get明細区分())) {
            // 消費税の行なのでスキップ
            return null;
        }
        if (BigDecimal.ZERO.compareTo(item.get数量()) == 0) {
            // 数量0の場合、数量1にする
            log.warn(String.format("数量が0以下なので数量1に変更しました。伝票番号:%s 伝票明細番号:%d 商品コード:%s", item.get伝票番号(), item.get行(), item.get商品コード()));
            item.set数量(BigDecimal.ONE);
        }
        if ("99999999".equals(item.get商品コード())) {
            // 商品コード99999999は手打ち商品なので商品名を16進数文字列でMD5値を取得する
            String hexString = DigestUtils.md5DigestAsHex(item.get商品名().getBytes());
            item.set商品コード(hexString);
            // 商品名に(手入力)を追加する
            item.set商品名(String.format("（手入力）%s", item.get商品名()));
        }
        if (BigDecimal.ONE.compareTo(item.get入数()) > 0) {
            // 入り数は最低1を設定
            item.set入数(BigDecimal.ONE);
        }
        if (BigDecimal.ZERO.compareTo(item.get単価()) == 0 && BigDecimal.ZERO.compareTo(item.get金額()) != 0) {
            // 単価が0円で小計金額が入っている場合は数量(絶対値)で割った金額を設定する
            BigDecimal goodsPrice = item.get金額().divide(item.get数量().abs(), 2, java.math.RoundingMode.HALF_UP);
            item.set単価(goodsPrice);
        }

        if (BigDecimal.ZERO.compareTo(item.get原単価()) >= 0) {
            // 仕入原価が0円以下
            log.warn(String.format("仕入原価が0円未満です。伝票番号：%s,行：%s,仕入原価：%s", item.get伝票番号(), item.get行(), item.get原単価()));
        }
        return item;
    }

    private Integer getShopNoFromItem(SmileOrderFile item) {
        String slipNumber = item.get伝票番号();
        if (slipNumber.length() == 8 && (slipNumber.startsWith("8") || slipNumber.startsWith("9"))) {
            return OfficeShopNo.B_CART_ORDER.getValue();
        }

        OfficeCode officeCode = OfficeCode.purse(item.get得意先営業所コード());
        if (officeCode == null) {
            return OfficeShopNo.INNER_ORDER.getValue();
        }
        switch (officeCode) {
            case DAINI:
                return OfficeShopNo.DAINI.getValue();
            case CLEAN_LABO:
                return OfficeShopNo.CLEAN_LABO.getValue();
            case DAIICHI:
                return OfficeShopNo.DAIICHI.getValue();
            case INNER_PURCHASE:
                return OfficeShopNo.INNER_PURCHASE.getValue();
            case INNER_ORDER:
                return OfficeShopNo.INNER_ORDER.getValue();
            default:
                return 0;
        }
    }
}
