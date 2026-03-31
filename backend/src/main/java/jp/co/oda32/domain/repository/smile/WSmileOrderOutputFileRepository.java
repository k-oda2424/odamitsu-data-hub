package jp.co.oda32.domain.repository.smile;

import jp.co.oda32.domain.model.embeddable.WSmileOrderOutputFilePK;
import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface WSmileOrderOutputFileRepository extends JpaRepository<WSmileOrderOutputFile, WSmileOrderOutputFilePK> {

    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE w_smile_order_output_file", nativeQuery = true)
    void truncateTable();

    @Query(value = "SELECT wsoof.* FROM w_smile_order_output_file wsoof " +
            "LEFT JOIN t_delivery_detail td " +
            "ON wsoof.shori_renban = td.processing_serial_number " +
            "AND wsoof.gyou = td.order_detail_no " +
            "AND td.shop_no = wsoof.shop_no " +
            "WHERE td.processing_serial_number IS NULL " +
            "ORDER BY wsoof.shop_no, wsoof.shori_renban",
            countQuery = "SELECT COUNT(*) FROM w_smile_order_output_file wsoof " +
                    "LEFT JOIN t_delivery_detail td " +
                    "ON wsoof.shori_renban = td.processing_serial_number " +
                    "AND wsoof.gyou = td.order_detail_no " +
                    "AND td.shop_no = wsoof.shop_no " +
                    "WHERE td.processing_serial_number IS NULL",
            nativeQuery = true)
    Page<WSmileOrderOutputFile> findNewOrders(Pageable pageable);

    // 既存注文かつ数量または商品が異なる注文を抽出するクエリ
    @Query(value = "SELECT wsoof.* FROM w_smile_order_output_file wsoof " +
            "LEFT JOIN t_delivery_detail td ON wsoof.shori_renban = td.processing_serial_number " +
            "AND wsoof.gyou = td.order_detail_no " +
            "AND td.shop_no = wsoof.shop_no " +
            "LEFT JOIN t_order_detail od ON td.order_no = od.order_no " +
            "AND td.order_detail_no = od.order_detail_no " +
            "LEFT JOIN t_order o ON od.order_no = o.order_no " +
            "WHERE (td.delivery_num != wsoof.suuryou " +
            "OR td.goods_code != wsoof.shouhin_code " +
            "OR wsoof.denpyou_hizuke != (select d.slip_date from t_delivery d where td.delivery_no = d.delivery_no) " +
            "OR od.goods_name != wsoof.shouhin_mei " +  // 商品名の変更
            "OR od.goods_price != wsoof.tanka " +  // 商品単価の変更
            "OR od.tax_rate != wsoof.shouhizeiritsu " +  // 消費税率の変更
            "OR od.tax_type != wsoof.kazei_kubun " +  // 課税区分の変更
            "OR o.partner_code != wsoof.tokuisaki_code) " +  // 得意先コードの変更
            "ORDER BY wsoof.shop_no, wsoof.shori_renban", nativeQuery = true)
    Page<WSmileOrderOutputFile> findModifiedOrders(Pageable pageable);



    Page<WSmileOrderOutputFile> findByTorihikiKubun(BigDecimal torihikikubun, Pageable pageable);

    List<WSmileOrderOutputFile> findByShopNoAndShoriRenban(int shopNo, long shorirenban);
}
