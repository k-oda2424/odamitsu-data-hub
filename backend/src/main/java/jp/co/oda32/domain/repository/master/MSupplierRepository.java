package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 仕入先マスタ(m_supplier)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/07/25
 */
public interface MSupplierRepository extends JpaRepository<MSupplier, Integer>, JpaSpecificationExecutor<MSupplier> {
    MSupplier getByShopNoAndSupplierCode(@Param("shopNo") Integer shopNo, @Param("supplierCode") String supplierCode);

    List<MSupplier> findByShopNo(@Param("shopNo") Integer shopNo);
}
