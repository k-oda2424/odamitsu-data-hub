package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.List;

/**
 * BCartのMemberAPIのレスポンスEntityクラス
 *
 * @author k_oda
 * @since 2023/06/27
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class BCartMemberApiResponse {
    @SerializedName("customers")// bcartからのレスポンスにはcustomersで返ってくるため
    private List<BCartMember> bCartMemberList;
}