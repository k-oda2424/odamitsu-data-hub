package jp.co.oda32.domain.repository.bcart;


import jp.co.oda32.domain.model.bcart.BCartVolumeDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BCartVolumeDiscountRepository extends JpaRepository<BCartVolumeDiscount, Long> {

    List<BCartVolumeDiscount> findByProductSetIdAndCustomerIdIsNull(Long productSetId);

    List<BCartVolumeDiscount> findByProductSetIdAndCustomerId(Long productSetId, Long customerId);

    List<BCartVolumeDiscount> findByVolumeDiscountIdIn(List<Long> volumeDiscountIdList);
}
