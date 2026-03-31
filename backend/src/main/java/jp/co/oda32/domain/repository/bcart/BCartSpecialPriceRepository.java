package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.productSets.BCartSpecialPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BCartSpecialPriceRepository extends JpaRepository<BCartSpecialPrice, Long> {
    BCartSpecialPrice getByProductSetIdAndCustomerId(Long productSetId, Long customerId);

    List<BCartSpecialPrice> findByProductSetId(Long productSetId);
}
