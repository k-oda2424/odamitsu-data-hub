package jp.co.oda32.domain.service.util;

import jp.co.oda32.domain.service.data.LoginUser;
import org.springframework.security.core.context.SecurityContextHolder;

public final class LoginUserUtil {

    private LoginUserUtil() {}

    public static LoginUser getLoginUserInfo() throws Exception {
        Object principal;
        try {
            principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        } catch (Exception e) {
            throw new Exception("ログインしていません");
        }
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        throw new Exception("ログインしていません");
    }

    /**
     * リクエストで指定された shopNo を、ログインユーザの権限で正規化する。
     * - admin（shopNo=0）: 指定値をそのまま返す（null可 = 全店舗）
     * - 非admin: 指定値が null またはログインユーザのshopNo以外なら強制上書き
     */
    public static Integer resolveEffectiveShopNo(Integer requested) {
        try {
            Integer loginShopNo = getLoginUserInfo().getUser().getShopNo();
            if (loginShopNo == null || loginShopNo == 0) {
                return requested;
            }
            return loginShopNo;
        } catch (Exception e) {
            return requested;
        }
    }
}
