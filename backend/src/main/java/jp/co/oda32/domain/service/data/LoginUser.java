package jp.co.oda32.domain.service.data;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.domain.model.master.MLoginUser;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.ArrayList;
import java.util.List;

public class LoginUser extends org.springframework.security.core.userdetails.User {
    private final MLoginUser loginUser;

    public LoginUser(MLoginUser user) {
        super(user.getLoginId(), user.getPassword(),
                AuthorityUtils.createAuthorityList(resolveRoles(user.getCompanyType())));
        this.loginUser = user;
    }

    private static String[] resolveRoles(String companyType) {
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");
        CompanyType type = CompanyType.purse(companyType);
        if (type != null) {
            switch (type) {
                case ADMIN -> roles.add("ROLE_ADMIN");
                case SHOP -> roles.add("ROLE_SHOP");
                case PARTNER -> roles.add("ROLE_PARTNER");
            }
        }
        return roles.toArray(new String[0]);
    }

    public MLoginUser getUser() {
        return this.loginUser;
    }
}
