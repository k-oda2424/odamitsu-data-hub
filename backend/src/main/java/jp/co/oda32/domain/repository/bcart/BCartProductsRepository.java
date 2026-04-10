package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartProducts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BCartProductsRepository extends JpaRepository<BCartProducts, Integer>,
        JpaSpecificationExecutor<BCartProducts> {

    List<BCartProducts> findByFlagOrderByPriorityDesc(String flag);

    List<BCartProducts> findByCategoryIdOrderByPriorityDesc(Integer categoryId);

    Optional<BCartProducts> findById(Integer id);
}
