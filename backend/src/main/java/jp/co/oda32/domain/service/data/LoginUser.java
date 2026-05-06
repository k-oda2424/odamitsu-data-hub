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

    /**
     * Admin 判定 (T4): shopNo == 0 かつ Role に ADMIN を含む。
     * 両者が乖離する場合 (DB 設定不整合等) は false を返す (fail-safe)。
     *
     * <p>用途:
     * <ul>
     *   <li>{@code @PreAuthorize("@loginUserSecurityBean.isAdmin()")} で全 Controller 統一</li>
     *   <li>service 層での admin 分岐 (例: 全 shop データへのアクセス)</li>
     * </ul>
     *
     * <p>shopNo は {@link MLoginUser#getShopNo()} 経由で解決する
     * (CompanyType=ADMIN なら 0、SHOP/PARTNER なら所属 shop_no)。
     */
    public boolean isAdmin() {
        if (loginUser == null) return false;
        Integer shopNo = loginUser.getShopNo();
        if (shopNo == null || shopNo != 0) return false;
        if (getAuthorities() == null) return false;
        return getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
