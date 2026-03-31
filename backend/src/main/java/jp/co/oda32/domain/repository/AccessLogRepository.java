package jp.co.oda32.domain.repository;

import jp.co.oda32.domain.model.TAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * アクセスログ(t_access_log)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2017/11/06
 */
public interface AccessLogRepository extends JpaRepository<TAccessLog, Integer>, JpaSpecificationExecutor<TAccessLog> {

}
