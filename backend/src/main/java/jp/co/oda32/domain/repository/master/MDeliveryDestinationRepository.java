package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.order.MDeliveryDestination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 出荷届け先(m_delivery_destination)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/12/15
 */
public interface MDeliveryDestinationRepository extends JpaRepository<MDeliveryDestination, Integer>, JpaSpecificationExecutor<MDeliveryDestination> {
    List<MDeliveryDestination> findByCompanyNo(@Param("companyNo") Integer companyNo);

    List<MDeliveryDestination> findByPartnerNo(@Param("partnerNo") Integer partnerNo);

    List<MDeliveryDestination> findByCompanyNoAndDestinationCode(@Param("companyNo") Integer companyNo, @Param("destinationCode") String destinationCode) throws Exception;
}
