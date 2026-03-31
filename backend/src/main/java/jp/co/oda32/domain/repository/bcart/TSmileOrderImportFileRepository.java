package jp.co.oda32.domain.repository.bcart;

/**
 * @author k_oda
 * @since 2023/04/10
 */

import jp.co.oda32.domain.model.bcart.TSmileOrderImportFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TSmileOrderImportFileRepository extends JpaRepository<TSmileOrderImportFile, Integer> {

//    List<SmileOrderImportFile> findByProcessingSerialNumberIsNull();

    List<TSmileOrderImportFile> findByCsvExportedFalse();

    List<TSmileOrderImportFile> findByAsanaTaskAddFalseAndPsnUpdatedTrue();

    List<TSmileOrderImportFile> findBybCartOrderIdIn(List<Long> bCartOrderIds);

    List<TSmileOrderImportFile> findBybCartLogisticsIdIn(List<Long> bCartLogisticsIds);

    List<TSmileOrderImportFile> findBySlipNumberAndProductCode(Integer slipNo, String productCode);

//    @Modifying
//    @Transactional
//    @Query(value = "TRUNCATE TABLE w_smile_order_import_file", nativeQuery = true)
//    void truncateTable();

    @Modifying
    @Transactional
    @Query(value = "UPDATE public.t_smile_order_import_file w "
            + "SET processing_serial_number = td.processing_serial_number "
            + ",psn_updated = true "
            + "FROM public.t_delivery_detail td "
            + "WHERE w.slip_number = CAST(td.slip_no AS integer) "
            + "AND (w.product_code = td.goods_code "
            + "     OR (w.product_code = '99999999' AND "
            + "         md5(w.product_name) = td.goods_code)) "
            + "AND CAST(w.quantity AS integer) = td.delivery_num "
            + "AND w.processing_serial_number IS NOT NULL "
            + "AND LENGTH(CAST(w.processing_serial_number AS TEXT)) = 10 "
            + "AND td.processing_serial_number IS NOT NULL", nativeQuery = true)
    void updateProcessingSerialNumbers();

}
