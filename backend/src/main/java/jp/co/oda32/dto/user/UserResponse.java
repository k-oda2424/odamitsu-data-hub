package jp.co.oda32.dto.user;

import jp.co.oda32.domain.model.master.MLoginUser;
import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Builder
public class UserResponse {
    private Integer loginUserNo;
    private String loginId;
    private String userName;
    private Integer companyNo;
    private String companyType;
    private Timestamp addDateTime;

    public static UserResponse from(MLoginUser user) {
        return UserResponse.builder()
                .loginUserNo(user.getLoginUserNo())
                .loginId(user.getLoginId())
                .userName(user.getUserName())
                .companyNo(user.getCompanyNo())
                .companyType(user.getCompanyType())
                .addDateTime(user.getAddDateTime())
                .build();
    }
}
