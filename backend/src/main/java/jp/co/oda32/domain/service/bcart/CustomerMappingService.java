package jp.co.oda32.domain.service.bcart;

/**
 * 使わないようにした。BCartMemberエンティティに機能を移行
 *
 * @author k_oda
 * @since 2023/04/10
 */

import jp.co.oda32.domain.model.bcart.CustomerMapping;
import jp.co.oda32.domain.repository.bcart.CustomerMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerMappingService {

    private final CustomerMappingRepository customerMappingRepository;

    @Transactional
    public CustomerMapping save(CustomerMapping customerMapping) {
        return customerMappingRepository.save(customerMapping);
    }

    public CustomerMapping getByBCartCustomerId(Long bCartCustomerId) {
        return this.customerMappingRepository.getCustomerMappingBybCartCustomerId(bCartCustomerId);
    }
}
