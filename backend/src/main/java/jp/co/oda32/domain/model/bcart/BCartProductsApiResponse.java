package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.List;

/**
 * BCartのProductsAPIのレスポンスEntityクラス
 *
 * @author k_oda
 * @since 2023/04/21
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class BCartProductsApiResponse {
    @SerializedName("products")
    private List<BCartProducts> bCartProductsList;
}