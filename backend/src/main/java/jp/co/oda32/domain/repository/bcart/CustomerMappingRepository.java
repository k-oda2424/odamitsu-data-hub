package jp.co.oda32.domain.repository.bcart;

/**
 * @author k_oda
 * @since 2023/04/10
 */

import jp.co.oda32.domain.model.bcart.CustomerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerMappingRepository extends JpaRepository<CustomerMapping, Integer> {

    CustomerMapping getCustomerMappingBybCartCustomerId(Long bCartCustomerId);
}
