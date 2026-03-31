package jp.co.oda32.dto.master;

import jp.co.oda32.domain.model.master.MShop;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShopResponse {
    private Integer shopNo;
    private String shopName;

    public static ShopResponse from(MShop s) {
        return ShopResponse.builder()
                .shopNo(s.getShopNo())
                .shopName(s.getShopName())
                .build();
    }
}
