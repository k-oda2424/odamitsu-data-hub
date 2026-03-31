package jp.co.oda32.batch.purchase;

import jp.co.oda32.util.StringUtil;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * 商品ファイル取込バッチProcessorクラス
 *
 * @author k_oda
 * @since 2018/07/18
 */
@Component
public class PurchaseMasterFileProcessor implements ItemProcessor<PurchaseMasterFile, PurchaseMasterFile> {

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
    public PurchaseMasterFile process(PurchaseMasterFile item) {
        if (StringUtil.isEmpty(item.get仕入先名１()) || StringUtil.isEmpty(item.get仕入先名略称())
                || item.get仕入先名１().contains("休止") || item.get仕入先名略称().contains("休止")) {
            // 仕入先名が空白,nullの場合登録しない
            return null;
        }
        if (StringUtil.isEqual("通常", item.get支払先区分())) {
            // 支払先区分が0(通常)の場合、使用していないデータなのでスキップ
            return null;
        }
        return item;
    }
}
