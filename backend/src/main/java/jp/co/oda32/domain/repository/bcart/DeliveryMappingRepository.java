package jp.co.oda32.domain.repository.bcart;

/**
 * @author k_oda
 * @since 2023/04/10
 */

import jp.co.oda32.domain.model.bcart.DeliveryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryMappingRepository extends JpaRepository<DeliveryMapping, Integer> {

    List<DeliveryMapping> findBybCartCustomerId(Long bCartCustomerId);

    List<DeliveryMapping> findBySmileCsvOutputtedFalse();

    /**
     * 指定 bCartCustomerId 配下の smile_delivery_code の最大値（数値化）を返す。
     * 連番採番で `size()+1` を使うと同一 batch 内で重複する race condition を起こすため、
     * DB から MAX(CAST(code AS INT)) を取得して +1 する運用にする。空なら 0 を返す。
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(smile_delivery_code AS INTEGER)), 0) " +
            "FROM delivery_mapping " +
            "WHERE b_cart_customer_id = :customerId " +
            "  AND smile_delivery_code ~ '^[0-9]+$'",
            nativeQuery = true)
    Integer findMaxSmileDeliveryCodeNumber(@Param("customerId") Long customerId);
}