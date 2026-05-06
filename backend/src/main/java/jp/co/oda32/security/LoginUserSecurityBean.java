package jp.co.oda32.security;

import jp.co.oda32.domain.service.data.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 認可マトリクス統一 (T4) の SpEL 用 Bean。
 *
 * <p>{@code @PreAuthorize("@loginUserSecurityBean.isAdmin()")} の形で全 Controller
 * から admin 判定を統一的に呼び出す。
 *
 * <p>従来は以下 3 系統が並存していた:
 * <ol>
 *   <li>{@code @PreAuthorize("hasRole('ADMIN')")} — Spring Security Role 経由</li>
 *   <li>{@code @PreAuthorize("authentication.principal.shopNo == 0")} — shopNo 直接</li>
 *   <li>service 層での {@code LoginUserUtil.resolveEffectiveShopNo(shopNo)} 呼出</li>
 * </ol>
 * 本 Bean は (1)(2) を {@link LoginUser#isAdmin()} の単一定義に集約する。
 * (3) は別目的 (shop user の自 shop ガード) であり継続使用。
 *
 * @see LoginUser#isAdmin()
 */
@Component("loginUserSecurityBean")
public class LoginUserSecurityBean {

    /**
     * 現在の認証 principal が admin (shopNo=0 かつ ROLE_ADMIN 保有) かを返す。
     *
     * <p>未認証 / principal が {@link LoginUser} でない場合は false (fail-safe)。
     */
    public boolean isAdmin() {
        LoginUser loginUser = currentLoginUser();
        return loginUser != null && loginUser.isAdmin();
    }

    /**
     * 認証済かつ admin でないこと (= shop user / partner user) を返す。
     *
     * <p>未認証 / principal が {@link LoginUser} でない場合は false。
     */
    public boolean isShopUser() {
        LoginUser loginUser = currentLoginUser();
        if (loginUser == null || loginUser.getUser() == null) return false;
        Integer shopNo = loginUser.getUser().getShopNo();
        return shopNo != null && shopNo != 0;
    }

    private LoginUser currentLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
