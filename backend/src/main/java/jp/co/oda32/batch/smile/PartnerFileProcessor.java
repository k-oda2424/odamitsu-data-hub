package jp.co.oda32.batch.smile;

import jp.co.oda32.constant.Constants;
import jp.co.oda32.util.StringUtil;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * 得意先ファイル取込バッチProcessorクラス
 *
 * @author k_oda
 * @since 2024/06/11
 */
@Component
public class PartnerFileProcessor implements ItemProcessor<PartnerFile, PartnerFile> {

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
    public PartnerFile process(PartnerFile item) {
        if (StringUtil.isEmpty(item.get得意先コード()) || StringUtil.isEqual(item.get得意先コード(), Constants.FIXED_PARTNER_CODE)) {
            // 得意先コードが空白,手打ちの場合登録しない
            return null;
        }
        if (item.get得意先名1().contains("休止") || item.get得意先名略称().contains("休止")) {
            return null;
        }
        return item;
    }
}
