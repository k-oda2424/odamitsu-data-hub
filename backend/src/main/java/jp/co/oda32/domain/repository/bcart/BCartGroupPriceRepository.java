package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.productSets.BCartGroupPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BCartGroupPriceRepository extends JpaRepository<BCartGroupPrice, Long> {
    List<BCartGroupPrice> findByProductSetId(Long productSetId);
}
