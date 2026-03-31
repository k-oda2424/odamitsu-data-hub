package jp.co.oda32.domain.repository.bcart;

/**
 * @author k_oda
 * @since 2023/04/10
 */

import jp.co.oda32.domain.model.bcart.DeliveryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryMappingRepository extends JpaRepository<DeliveryMapping, Integer> {

    List<DeliveryMapping> findBybCartCustomerId(Long bCartCustomerId);

    List<DeliveryMapping> findBySmileCsvOutputtedFalse();
}