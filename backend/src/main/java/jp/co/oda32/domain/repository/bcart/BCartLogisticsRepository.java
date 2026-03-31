package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartLogistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BCartのOrdersAPIの出荷情報レスポンスを保持するテーブルに対するリポジトリクラス
 *
 * @author k_oda
 * @since 2023/03/24
 */
@Repository
public interface BCartLogisticsRepository extends JpaRepository<BCartLogistics, Long>, JpaSpecificationExecutor<BCartLogistics> {
    @Query("SELECT b FROM BCartLogistics b WHERE b.status IN (:statuses)")
    List<BCartLogistics> findByStatusIn(@Param("statuses") List<String> statuses);

    @Query(value =
            "SELECT DISTINCT bl.* FROM b_cart_logistics bl " +
                    "JOIN b_cart_order_product bop ON bl.id = bop.logistics_id " +
                    "JOIN b_cart_order bo ON bop.order_id = bo.id " +
                    "WHERE bl.status = :status",
            nativeQuery = true)
    List<BCartLogistics> findByStatusNative(@Param("status") String status);

    List<BCartLogistics> findByIdIn(List<Long> idList);

    @Query("SELECT b FROM BCartLogistics b WHERE (b.bCartCsvExported = false AND b.status = '発送済') OR (b.bCartCsvExported = true AND b.isUpdated = true)")
    List<BCartLogistics> findExportableRecords();

    @Modifying
    @Transactional
    @Query(value = "UPDATE public.b_cart_logistics bcl "
            + "SET shipment_code = tsoif.processing_serial_number "
            + "FROM public.t_smile_order_import_file tsoif "
            + "WHERE bcl.id = tsoif.b_cart_logistics_id "
            + "AND psn_updated "
            + "AND (shipment_code IS NULL OR shipment_code = '')", nativeQuery = true)
    void updateShipmentCodes();
}