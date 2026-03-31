package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MTaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 消費税率マスタ(m_tax_rate)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2022/12/01
 */
public interface MTaxRateRepository extends JpaRepository<MTaxRate, Integer>, JpaSpecificationExecutor<MTaxRate> {

}
