package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartVolumeDiscount;
import jp.co.oda32.domain.repository.bcart.BCartVolumeDiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BCartVolumeDiscountService {

    @Autowired
    private BCartVolumeDiscountRepository bCartVolumeDiscountRepository;

    public List<BCartVolumeDiscount> findByProductSetIdAndCustomerId(Long productSetId, Long customerId) {
        if (customerId == null) {
            return this.bCartVolumeDiscountRepository.findByProductSetIdAndCustomerIdIsNull(productSetId);
        }
        return this.bCartVolumeDiscountRepository.findByProductSetIdAndCustomerId(productSetId, customerId);
    }

    public List<BCartVolumeDiscount> findByVolumeDiscountIdList(List<Long> volumeDiscountIdList) {
        return this.bCartVolumeDiscountRepository.findByVolumeDiscountIdIn(volumeDiscountIdList);
    }

    @Transactional
    public BCartVolumeDiscount save(BCartVolumeDiscount bCartVolumeDiscount) {
        return bCartVolumeDiscountRepository.save(bCartVolumeDiscount);
    }

    @Transactional
    public List<BCartVolumeDiscount> saveAll(List<BCartVolumeDiscount> bCartVolumeDiscountList) {
        return bCartVolumeDiscountRepository.saveAll(bCartVolumeDiscountList);
    }
}
