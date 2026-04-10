package jp.co.oda32.dto.master;

import jp.co.oda32.domain.model.master.MShopLinkedFile;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShopLinkedFileResponse {
    private Integer shopNo;
    private String shopName;
    private String smileOrderInputFileName;
    private String smilePurchaseFileName;
    private String smileOrderOutputFileName;
    private String smilePartnerOutputFileName;
    private String smileDestinationOutputFileName;
    private String smileGoodsImportFileName;
    private String bCartLogisticsImportFileName;
    private String invoiceFilePath;

    public static ShopLinkedFileResponse from(MShopLinkedFile f) {
        String shopName = f.getShop() != null ? f.getShop().getShopName() : "Shop " + f.getShopNo();
        return ShopLinkedFileResponse.builder()
                .shopNo(f.getShopNo())
                .shopName(shopName)
                .smileOrderInputFileName(f.getSmileOrderInputFileName())
                .smilePurchaseFileName(f.getSmilePurchaseFileName())
                .smileOrderOutputFileName(f.getSmileOrderOutputFileName())
                .smilePartnerOutputFileName(f.getSmilePartnerOutputFileName())
                .smileDestinationOutputFileName(f.getSmileDestinationOutputFileName())
                .smileGoodsImportFileName(f.getSmileGoodsImportFileName())
                .bCartLogisticsImportFileName(f.getBCartLogisticsImportFileName())
                .invoiceFilePath(f.getInvoiceFilePath())
                .build();
    }
}
