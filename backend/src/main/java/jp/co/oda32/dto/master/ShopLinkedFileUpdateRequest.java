package jp.co.oda32.dto.master;

import lombok.Data;

@Data
public class ShopLinkedFileUpdateRequest {
    private String smileOrderInputFileName;
    private String smilePurchaseFileName;
    private String smileOrderOutputFileName;
    private String smilePartnerOutputFileName;
    private String smileDestinationOutputFileName;
    private String smileGoodsImportFileName;
    private String bCartLogisticsImportFileName;
    private String invoiceFilePath;
}
