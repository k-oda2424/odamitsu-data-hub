package jp.co.oda32.domain.repository.smile;

import jp.co.oda32.domain.model.embeddable.WSmilePurchaseOutputFilePK;
import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.util.List;

/**
 * WSmilePurchaseOutputFileのリポジトリ
 *
 * @author k_oda
 * @since 2024/09/11
 */
@Repository
public interface WSmilePurchaseOutputFileRepository extends JpaRepository<WSmilePurchaseOutputFile, WSmilePurchaseOutputFilePK> {
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "TRUNCATE TABLE w_smile_purchase_output_file RESTART IDENTITY", nativeQuery = true)
    void truncateTable();

    @Query(value = "SELECT wspof.* FROM w_smile_purchase_output_file wspof " +
            "LEFT JOIN t_purchase_detail tpd " +
            "ON wspof.shori_renban = tpd.ext_purchase_no " +
            "AND wspof.gyou = tpd.purchase_detail_no " +
            "AND tpd.shop_no = wspof.shop_no " +
            "WHERE tpd.ext_purchase_no IS NULL " +
            "ORDER BY wspof.shop_no, wspof.shori_renban",
            countQuery = "SELECT COUNT(*) FROM w_smile_purchase_output_file wspof " +
                    "LEFT JOIN t_purchase_detail tpd " +
                    "ON wspof.shori_renban = tpd.ext_purchase_no " +
                    "AND wspof.gyou = tpd.purchase_detail_no " +
                    "AND tpd.shop_no = wspof.shop_no " +
                    "WHERE tpd.ext_purchase_no IS NULL ",
            nativeQuery = true)
    Page<WSmilePurchaseOutputFile> findNewPurchases(Pageable pageable);

    // 既存仕入かつ数量または商品が異なる仕入を抽出するクエリ
    @Query(value = "SELECT wspof.* FROM w_smile_purchase_output_file wspof " +
            "JOIN t_purchase_detail tpd ON wspof.shori_renban = tpd.ext_purchase_no " +
            "AND wspof.gyou = tpd.purchase_detail_no " +
            "AND tpd.shop_no = wspof.shop_no " +
            "JOIN t_purchase tp ON tpd.purchase_no = tp.purchase_no " +
            "WHERE (tpd.goods_num != wspof.suuryou " +
            "OR tpd.goods_code != wspof.shouhin_code " +
            "OR wspof.denpyou_hizuke != tp.purchase_date " +
            "OR tpd.goods_name != wspof.shouhin_mei " +  // 商品名の変更
            "OR tpd.goods_price != wspof.tanka " +  // 商品単価の変更
            "OR COALESCE(tpd.subtotal, 0) != COALESCE(wspof.kingaku, 0) " +  // 税抜小計の変更（値引の場合金額はここにしか入っていないため）
            "OR tpd.tax_rate != wspof.shouhizeiritsu " +  // 消費税率の変更
            "OR tpd.tax_type != wspof.kazei_kubun) " +  // 課税区分の変更
            "ORDER BY wspof.shop_no, wspof.shori_renban",
            countQuery = "SELECT COUNT(*) FROM w_smile_purchase_output_file wspof " +
                    "JOIN t_purchase_detail tpd ON wspof.shori_renban = tpd.ext_purchase_no " +
                    "AND wspof.gyou = tpd.purchase_detail_no " +
                    "AND tpd.shop_no = wspof.shop_no " +
                    "JOIN t_purchase tp ON tpd.purchase_no = tp.purchase_no " +
                    "WHERE (tpd.goods_num != wspof.suuryou " +
                    "OR tpd.goods_code != wspof.shouhin_code " +
                    "OR wspof.denpyou_hizuke != tp.purchase_date " +
                    "OR tpd.goods_name != wspof.shouhin_mei " +  // 商品名の変更
                    "OR tpd.goods_price != wspof.tanka " +  // 商品単価の変更
                    "OR tpd.subtotal != wspof.kingaku " +  // 税抜小計の変更（値引の場合金額はここにしか入っていないため）
                    "OR tpd.tax_rate != wspof.shouhizeiritsu " +  // 消費税率の変更
                    "OR tpd.tax_type != wspof.kazei_kubun) ",  // 課税区分の変更
            nativeQuery = true)
    Page<WSmilePurchaseOutputFile> findModifiedPurchases(Pageable pageable);


    List<WSmilePurchaseOutputFile> findByShopNoAndShoriRenban(int shopNo, long shorirenban);
}
