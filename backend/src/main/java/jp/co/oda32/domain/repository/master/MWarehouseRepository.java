package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 倉庫マスタ(m_warehouse)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/04/11
 */
public interface MWarehouseRepository extends JpaRepository<MWarehouse, Integer>, JpaSpecificationExecutor<MWarehouse> {
    List<MWarehouse> findAll();

    List<MWarehouse> findByWarehouseName(@Param("warehouseName") String warehouseName);
}
