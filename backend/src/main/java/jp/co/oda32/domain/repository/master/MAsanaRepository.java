package jp.co.oda32.domain.repository.master;

/**
 * asanaマスタのリポジトリクラス
 *
 * @author k_oda
 * @since 2024/05/04
 */

import jp.co.oda32.domain.model.master.MAsana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface MAsanaRepository extends JpaRepository<MAsana, BigDecimal> {

}
