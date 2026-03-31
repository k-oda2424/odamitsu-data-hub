package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.master.MPaymentSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 支払先マスタ(m_payment_supplier)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/06/21
 */
public interface MPaymentSupplierRepository extends JpaRepository<MPaymentSupplier, Integer>, JpaSpecificationExecutor<MPaymentSupplier> {
    List<MPaymentSupplier> findByShopNoAndPaymentSupplierCode(@Param("shopNo") Integer shopNo, @Param("paymentSupplierCode") String paymentSupplierCode);
}
