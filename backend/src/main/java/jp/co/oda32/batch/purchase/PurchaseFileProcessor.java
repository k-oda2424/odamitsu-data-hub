package jp.co.oda32.batch.purchase;

import jp.co.oda32.constant.Constants;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.service.master.MCompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 仕入れファイル取込バッチProcessorクラス
 *
 * @author k_oda
 * @since 2019/06/26
 */
@Component
@StepScope
@Log4j2
@RequiredArgsConstructor
public class PurchaseFileProcessor implements ItemProcessor<PurchaseFile, ExtPurchaseFile> {
    @NonNull
    private MCompanyService mCompanyService;
    /**
     * shopNo → companyNo のキャッシュ。{@link jp.co.oda32.batch.purchase.ShopNoAwareItemReader}
     * が shop_no=1 の CSV と shop_no=2 の CSV を同一 chunk に混在させ得るため、
     * 最初に見た shop の companyNo を固定化するとダブル shop データで取り違えが起きる。
     */
    private final Map<Integer, Integer> companyNoByShop = new HashMap<>();

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
    public ExtPurchaseFile process(PurchaseFile item) throws Exception {
        Integer shopNo = item.getShopNo();
        Integer companyNo = companyNoByShop.get(shopNo);
        if (companyNo == null) {
            MCompany company = this.mCompanyService.getByShopNo(shopNo);
            if (company == null) {
                throw new Exception(String.format("ショップ番号に紐づく会社が見つかりません。ショップ番号:%s", shopNo));
            }
            companyNo = company.getCompanyNo();
            companyNoByShop.put(shopNo, companyNo);
        }
        ExtPurchaseFile extPurchaseFile = new ExtPurchaseFile();
        BeanUtils.copyProperties(item, extPurchaseFile);
        extPurchaseFile.set伝票日付(item.get伝票日付Str());
        extPurchaseFile.setCompanyNo(companyNo);

        if (Constants.FIXED_PRODUCT_CODE.equals(item.get商品コード())) {
            // 商品コード99999999は手打ち商品なので商品名を16進数文字列でMD5値を取得する
            String hexString = DigestUtils.md5DigestAsHex(item.get商品コード().getBytes());
            extPurchaseFile.set商品コード(hexString);
            // 手打ち商品は販売商品の登録有無はチェックしない
            return extPurchaseFile;
        }
        return extPurchaseFile;
    }
}
