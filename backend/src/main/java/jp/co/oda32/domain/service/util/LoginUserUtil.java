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
}
