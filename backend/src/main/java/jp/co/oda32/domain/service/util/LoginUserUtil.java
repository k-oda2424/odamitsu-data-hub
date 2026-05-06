package jp.co.oda32.domain.service.util;

import jp.co.oda32.domain.service.data.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
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
     *
     * <p>C-N5 (round 2 fix): 認証情報取得失敗時は fail-closed で
     * {@link AccessDeniedException} を throw する。
     * 旧実装はリクエスト値をそのまま返していたため、principal が
     * {@link LoginUser} 以外 (例: anonymousUser) の異常状態で
     * SF-02 / SF-03 の IDOR ガード (FinanceController 等) が完全に形骸化していた。
     * GlobalExceptionHandler が AccessDeniedException → HTTP 403 に変換する。
     *
     * @throws AccessDeniedException 認証情報を取得できない場合 (anonymous, principal 不一致, etc.)
     */
    public static Integer resolveEffectiveShopNo(Integer requested) {
        LoginUser loginUser;
        try {
            loginUser = getLoginUserInfo();
        } catch (Exception e) {
            log.warn("認証情報取得失敗 - fail closed: {}", e.getMessage());
            throw new AccessDeniedException("認証情報を取得できません");
        }
        Integer loginShopNo = loginUser.getUser().getShopNo();
        if (loginShopNo == null || loginShopNo == 0) {
            // admin: リクエスト値をそのまま返す (null = 全 shop)
            return requested;
        }
        // 非 admin: ログイン shop に強制上書き (Controller 側で requested と比較して 403)
        return loginShopNo;
    }
}
