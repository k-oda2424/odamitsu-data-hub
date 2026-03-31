package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.MPurchasePriceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 仕入価格マスタ履歴(m_purchase_price_log)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2020/01/21
 */
public interface MPurchasePriceLogRepository extends JpaRepository<MPurchasePriceLog, Integer>, JpaSpecificationExecutor<MPurchasePriceLog> {

}
