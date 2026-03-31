package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

/**
 * ログインユーザ検索条件
 *
 * @author k_oda
 * @since 2017/08/19
 */
public class LoginUserSpecification extends CommonSpecification<MLoginUser> {
    /**
     * ログインユーザ番号の検索条件
     *
     * @param loginUserNo ログインユーザ番号
     * @return ログインユーザ番号の検索条件
     */
    public Specification<MLoginUser> loginUserNoContains(Integer loginUserNo) {
        return loginUserNo == null ? null : (root, query, cb) -> cb.equal(root.get("loginUserNo"), loginUserNo);
    }

    /**
     * ログインユーザ名の検索条件
     *
     * @param userName ログインユーザ名
     * @return ログインユーザ名の検索条件
     */
    public Specification<MLoginUser> userNameContains(String userName) {
        return StringUtil.isEmpty(userName) ? null : (root, query, cb) -> cb.equal(root.get("userName"), userName);
    }

    /**
     * ログインIDの検索条件
     *
     * @param loginId ログインID
     * @return ログインIDの検索条件
     */
    public Specification<MLoginUser> loginIdContains(String loginId) {
        return StringUtil.isEmpty(loginId) ? null : (root, query, cb) -> cb.equal(root.get("loginId"), loginId);
    }

}
