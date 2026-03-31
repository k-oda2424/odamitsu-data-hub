package jp.co.oda32.batch.goods;

import jp.co.oda32.util.StringUtil;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;

/**
 * 商品ファイル取込バッチProcessorクラス
 *
 * @author k_oda
 * @since 2018/07/18
 */
@Component
public class GoodsFileProcessor implements ItemProcessor<GoodsFile, GoodsFile> {

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
    public GoodsFile process(GoodsFile item) {
        if (StringUtil.isEmpty(item.get商品名()) || item.get商品名().contains("休止")) {
            // 商品名が空白,nullの場合登録しない
            return null;
        }
        if ("99999999".equals(item.get商品コード())) {
            // 商品コード99999999は手打ち商品なので商品名を16進数文字列でMD5値を取得する
            String hexString = DigestUtils.md5DigestAsHex(item.get商品名().getBytes());
            item.set商品コード(hexString);
        }
        if (item.get入数().compareTo(BigDecimal.ONE) < 0) {
            // 入り数は最低1を設定
            item.set入数(BigDecimal.ONE);
        }
        if (!(item.getＪＡＮ().length() == 8 || item.getＪＡＮ().length() == 13)) {
            // janコードが8,13桁でない
            item.setＪＡＮ(null);
        }
        if (BigDecimal.ZERO.compareTo(item.get標準売上単価()) >= 0) {
            // 売価が0円以下
            item.set標準売上単価(null);
        }
        if (BigDecimal.ZERO.compareTo(item.get標準仕入単価()) >= 0) {
            // 仕入原価が0円以下
            item.set標準仕入単価(null);
        }
        return item;
    }
}
