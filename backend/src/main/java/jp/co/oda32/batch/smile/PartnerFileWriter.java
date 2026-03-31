package jp.co.oda32.batch.smile;

import jp.co.oda32.constant.OfficeCode;
import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.domain.model.master.WSmilePartner;
import jp.co.oda32.domain.service.master.WSmilePartnerService;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.batch.item.Chunk;

import java.util.List;

/**
 * 得意先ファイル取り込みバッチWriterクラス
 *
 * @author k_oda
 * @since 2024/06/11
 */
@Component
@StepScope
@Log4j2
public class PartnerFileWriter implements ItemWriter<PartnerFile> {

    @Autowired
    WSmilePartnerService wSmilePartnerService;

    public static void logPartner(PartnerFile item) {
        String 得意先名2 = item.get得意先名2() != null ? item.get得意先名2() : "";
        String 得意先名 = item.get得意先名1() + 得意先名2;
        log.info(String.format("partner_code:%s,得意先名:%s", item.get得意先コード(), 得意先名));
    }

    /**
     * Process the supplied data element. Will not be called with any null items
     * in normal operation.
     *
     * @param items items to be written
     * @throws Exception if there are errors. The framework will catch the
     *                   exception and convert or rethrow it as appropriate.
     */
    @Override
    public void write(Chunk<? extends PartnerFile> items) throws Exception {
        for (PartnerFile item : items) {
            logPartner(item);
            // w_smile_partnerテーブルに全登録
            WSmilePartner wSmilePartner = new WSmilePartner();
            BeanUtils.copyProperties(item, wSmilePartner);
            wSmilePartner.setShopNo(getShopNoFromItem(item));
            wSmilePartnerService.save(wSmilePartner);
        }
    }

    private Integer getShopNoFromItem(PartnerFile item) {
        OfficeCode officeCode = OfficeCode.purse(item.get営業所コード());
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
