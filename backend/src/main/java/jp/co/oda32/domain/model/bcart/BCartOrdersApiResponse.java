package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.List;

/**
 * BCartのOrdersAPIのレスポンスEntityクラス
 *
 * @author k_oda
 * @since 2023/03/21
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class BCartOrdersApiResponse {
    @SerializedName("orders")// bcartからのレスポンスにはordersで返ってくるため
    private List<BCartOrder> bCartOrderList;
}