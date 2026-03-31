package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MSupplierShopMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MSupplierShopMappingRepository extends JpaRepository<MSupplierShopMapping, Integer> {

    /**
     * ソースのショップ番号と仕入先コードから対応するマッピングを検索
     */
    @Query("SELECT m FROM MSupplierShopMapping m WHERE m.sourceShopNo = :shopNo AND m.sourceSupplierCode = :supplierCode AND m.delFlg = '0'")
    Optional<MSupplierShopMapping> findBySourceShopNoAndSupplierCode(
            @Param("shopNo") Integer shopNo,
            @Param("supplierCode") String supplierCode);

    /**
     * ターゲットのショップ番号と仕入先コードに対応するすべてのマッピングを検索
     */
    @Query("SELECT m FROM MSupplierShopMapping m WHERE m.targetShopNo = :shopNo AND m.targetSupplierCode = :supplierCode AND m.delFlg = '0'")
    List<MSupplierShopMapping> findAllByTargetShopNoAndSupplierCode(
            @Param("shopNo") Integer shopNo,
            @Param("supplierCode") String supplierCode);

    /**
     * ソースのショップ番号でマッピングを検索します。
     */
    @Query("SELECT m FROM MSupplierShopMapping m WHERE m.sourceShopNo = :shopNo AND m.delFlg = '0'")
    List<MSupplierShopMapping> findBySourceShopNo(@Param("shopNo") Integer shopNo);
}
