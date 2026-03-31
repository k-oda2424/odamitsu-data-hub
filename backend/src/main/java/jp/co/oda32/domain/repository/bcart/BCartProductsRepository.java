package jp.co.oda32.domain.repository.bcart;


import jp.co.oda32.domain.model.bcart.BCartProducts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author k_oda
 * @since 2023/04/21
 */
@Repository
public interface BCartProductsRepository extends JpaRepository<BCartProducts, Long> {
}
