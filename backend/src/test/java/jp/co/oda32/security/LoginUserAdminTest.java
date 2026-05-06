package jp.co.oda32.security;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.service.data.LoginUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4: {@link LoginUser#isAdmin()} 境界条件テスト。
 *
 * <p>判定式: shopNo == 0 かつ Role に ADMIN を含む。
 * 両者が乖離する場合は false (fail-safe)。
 */
class LoginUserAdminTest {

    @Test
    void shopNo0_かつ_RoleAdmin_でadmin判定true() {
        LoginUser u = newAdminUser();
        assertTrue(u.isAdmin());
    }

    @Test
    void shopNo0_だがRoleAdminなし_で_false_failsafe() {
        // CompanyType=SHOP かつ shopNo=0 を強制 → role は ROLE_SHOP, shopNo=0
        // しかし MLoginUser.getShopNo() は CompanyType=SHOP で company.shopNo を返すため
        // company.shopNo=0 を設定して shopNo=0 / role=ROLE_SHOP の組み合わせを再現する。
        MCompany company = new MCompany();
        company.setShopNo(0);
        MLoginUser m = MLoginUser.builder()
                .loginId("shop_with_zero")
                .password("pw")
                .companyType(CompanyType.SHOP.getValue())
                .build();
        m.setCompany(company);
        LoginUser u = new LoginUser(m);
        assertFalse(u.isAdmin(), "ROLE_ADMIN を持たないため false");
    }

    @Test
    void shopNo非0_で_false_failsafe() {
        // 不正 CompanyType → resolveRoles() で ROLE_USER のみ + getShopNo() = -1 (非 0)。
        // shopNo != 0 のため isAdmin() = false (Q1=(a) 同期前提下では到達しないが fail-safe 確認)。
        // 注: CompanyType=ADMIN なら getShopNo()=0 固定 / ROLE_SHOP+shopNo=0 ケースは
        //     shopNo0_だがRoleAdminなし_で_false_failsafe で別途カバー。
        MLoginUser m = MLoginUser.builder()
                .loginId("unknown_user")
                .password("pw")
                .companyType("invalid_type")
                .build();
        LoginUser u = new LoginUser(m);
        assertFalse(u.isAdmin(), "shopNo=-1 (≠0) で false");
    }

    @Test
    void shopNo_null_で_false() {
        // companyType=null → CompanyType.purse() = null → getShopNo() = -1 (≠0) で false
        MLoginUser m = MLoginUser.builder()
                .loginId("noctype")
                .password("pw")
                .companyType(null)
                .build();
        LoginUser u = new LoginUser(m);
        assertFalse(u.isAdmin());
    }

    @Test
    void admin_principal_でisShopUser_false() {
        // 補助確認: admin は shop user ではない
        LoginUser u = newAdminUser();
        // isAdmin=true / shopNo=0 のため、isShopUser 判定相当 (shopNo!=0) は false
        assertFalse(u.getUser().getShopNo() != 0);
    }

    /** CompanyType=ADMIN → ROLE_ADMIN 付与 + shopNo=0 固定 (OfficeShopNo.ADMIN)。 */
    private static LoginUser newAdminUser() {
        MLoginUser m = MLoginUser.builder()
                .loginId("admin")
                .password("pw")
                .companyType(CompanyType.ADMIN.getValue())
                .build();
        return new LoginUser(m);
    }
}
