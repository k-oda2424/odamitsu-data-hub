package jp.co.oda32.domain.service.bcart;

/**
 * @author k_oda
 * @since 2023/04/10
 */

import jp.co.oda32.domain.model.bcart.DeliveryMapping;
import jp.co.oda32.domain.repository.bcart.DeliveryMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeliveryMappingService {

    @Autowired
    private DeliveryMappingRepository deliveryMappingRepository;

    @Transactional
    public DeliveryMapping save(DeliveryMapping deliveryMapping) {
        return deliveryMappingRepository.save(deliveryMapping);
    }

    @Transactional
    public List<DeliveryMapping> saveAll(List<DeliveryMapping> deliveryMappings) {
        return deliveryMappingRepository.saveAll(deliveryMappings);
    }

    public List<DeliveryMapping> findBybCartCustomerId(Long bCartCustomerId) {
        return this.deliveryMappingRepository.findBybCartCustomerId(bCartCustomerId);
    }

    public List<DeliveryMapping> findBySmileCsvOutputtedFalse() {
        return this.deliveryMappingRepository.findBySmileCsvOutputtedFalse();
    }
}
