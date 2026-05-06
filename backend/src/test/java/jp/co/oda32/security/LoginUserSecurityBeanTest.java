package jp.co.oda32.security;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.service.data.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4: {@link LoginUserSecurityBean} の SpEL 経由判定テスト。
 *
 * <p>SecurityContextHolder に principal をセットし、isAdmin() / isShopUser() を確認する。
 */
class LoginUserSecurityBeanTest {

    private final LoginUserSecurityBean bean = new LoginUserSecurityBean();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void admin_principal_でisAdmin_true() {
        setPrincipal(newAdmin());
        assertTrue(bean.isAdmin());
        assertFalse(bean.isShopUser());
    }

    @Test
    void shop_user_principal_でisShopUser_true() {
        setPrincipal(newShopUser(2));
        assertFalse(bean.isAdmin());
        assertTrue(bean.isShopUser());
    }

    @Test
    void 未認証_で_両方_false() {
        // SecurityContext を空のままにする
        assertFalse(bean.isAdmin());
        assertFalse(bean.isShopUser());
    }

    @Test
    void anonymous_authentication_で_両方_false() {
        var anon = new AnonymousAuthenticationToken(
                "anonkey",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anon);
        assertFalse(bean.isAdmin());
        assertFalse(bean.isShopUser());
    }

    @Test
    void principal_がLoginUser以外_で_両方_false() {
        // principal が単なる String の場合
        var token = new UsernamePasswordAuthenticationToken(
                "stringPrincipal",
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(token);
        assertFalse(bean.isAdmin());
        assertFalse(bean.isShopUser());
    }

    private void setPrincipal(LoginUser user) {
        var token = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private static LoginUser newAdmin() {
        MLoginUser m = MLoginUser.builder()
                .loginId("admin")
                .password("pw")
                .companyType(CompanyType.ADMIN.getValue())
                .build();
        return new LoginUser(m);
    }

    private static LoginUser newShopUser(int shopNo) {
        MCompany company = new MCompany();
        company.setShopNo(shopNo);
        MLoginUser m = MLoginUser.builder()
                .loginId("shop_user")
                .password("pw")
                .companyType(CompanyType.SHOP.getValue())
                .build();
        m.setCompany(company);
        return new LoginUser(m);
    }
}
