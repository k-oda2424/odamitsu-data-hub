package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.List;

/**
 * B-CART Categories APIのレスポンスクラス
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class BCartCategoriesApiResponse {
    @SerializedName("categories")
    private List<BCartCategories> bCartCategoriesList;
}
