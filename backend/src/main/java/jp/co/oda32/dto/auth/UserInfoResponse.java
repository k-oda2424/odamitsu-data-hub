package jp.co.oda32.dto.auth;

import jp.co.oda32.domain.model.master.MLoginUser;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoResponse {
    private Integer loginUserNo;
    private String userName;
    private String loginId;
    private Integer companyNo;
    private String companyType;
    private Integer shopNo;

    public static UserInfoResponse from(MLoginUser user) {
        return UserInfoResponse.builder()
                .loginUserNo(user.getLoginUserNo())
                .userName(user.getUserName())
                .loginId(user.getLoginId())
                .companyNo(user.getCompanyNo())
                .companyType(user.getCompanyType())
                .shopNo(user.getShopNo())
                .build();
    }
}
