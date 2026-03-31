package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MLoginUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * ログインユーザマスタ(m_admin_user)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2017/05/02
 */
public interface LoginUserRepository extends JpaRepository<MLoginUser, Integer>, JpaSpecificationExecutor<MLoginUser> {
    List<MLoginUser> findAll();

    List<MLoginUser> findByUserName(@Param("userName") String userName);

    MLoginUser findByLoginId(@Param("loginId") String loginId);
}
