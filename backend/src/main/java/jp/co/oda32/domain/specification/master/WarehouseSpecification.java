package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.specification.CommonSpecification;

import org.springframework.data.jpa.domain.Specification;

/**
 * 倉庫検索条件
 *
 * @author k_oda
 * @since 2018/04/16
 */
public class WarehouseSpecification extends CommonSpecification<MWarehouse> {
    /**
     * 倉庫番号の検索条件
     *
     * @param warehouseNo 倉庫番号
     * @return 倉庫番号の検索条件
     */
    public Specification<MWarehouse> warehouseNoContains(Integer warehouseNo) {
        return warehouseNo == null ? null : (root, query, cb) -> cb.equal(root.get("warehouseNo"), warehouseNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<MWarehouse> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }
    /**
     * 倉庫名の検索条件
     *
     * @param warehouseName 倉庫名
     * @return 倉庫名の検索条件
     */
    public Specification<MWarehouse> warehouseNameContains(String warehouseName) {
        return likeNormalized("warehouseName", warehouseName);
    }

}
