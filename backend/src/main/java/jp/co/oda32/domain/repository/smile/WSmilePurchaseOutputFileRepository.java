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

    /**
     * 新規仕入としてワーク→本テーブルへ登録すべきレコードを検索する。
     * <p>第2事業部の月次集約仕入を表す商品コード
     * ({@code 00000021} / {@code 00000023}) は SMILE に手入力される事務処理用行で、
     * 実仕入は shop_no=2 側に別途存在するため、本テーブルには入れずに除外する
     * （詳細: {@link jp.co.oda32.constant.FinanceConstants#DIVISION2_AGGREGATE_GOODS_CODES}）。
     * ワークテーブルには CSV の内容をそのまま保持する方針のため、除外はこの検索段階で行う。
     */
    @Query(value = "SELECT wspof.* FROM w_smile_purchase_output_file wspof " +
            "LEFT JOIN t_purchase_detail tpd " +
            "ON wspof.shori_renban = tpd.ext_purchase_no " +
            "AND wspof.gyou = tpd.purchase_detail_no " +
            "AND tpd.shop_no = wspof.shop_no " +
            "WHERE tpd.ext_purchase_no IS NULL " +
            "AND wspof.shouhin_code NOT IN ('00000021','00000023') " +
            "ORDER BY wspof.shop_no, wspof.shori_renban",
            countQuery = "SELECT COUNT(*) FROM w_smile_purchase_output_file wspof " +
                    "LEFT JOIN t_purchase_detail tpd " +
                    "ON wspof.shori_renban = tpd.ext_purchase_no " +
                    "AND wspof.gyou = tpd.purchase_detail_no " +
                    "AND tpd.shop_no = wspof.shop_no " +
                    "WHERE tpd.ext_purchase_no IS NULL " +
                    "AND wspof.shouhin_code NOT IN ('00000021','00000023') ",
            nativeQuery = true)
    Page<WSmilePurchaseOutputFile> findNewPurchases(Pageable pageable);

    /**
     * 既存仕入のうち更新が必要なレコードを検索する。
     * 新規と同様に第2事業部集約行 (00000021/00000023) は本テーブル対象外のため除外する。
     */
    // NULL を含む列の変更検知を取りこぼさないよう、= 系列は全て PostgreSQL の
    // IS DISTINCT FROM を使って比較する ( `!=` は片側 NULL で UNKNOWN を返し除外される )。
    @Query(value = "SELECT wspof.* FROM w_smile_purchase_output_file wspof " +
            "JOIN t_purchase_detail tpd ON wspof.shori_renban = tpd.ext_purchase_no " +
            "AND wspof.gyou = tpd.purchase_detail_no " +
            "AND tpd.shop_no = wspof.shop_no " +
            "JOIN t_purchase tp ON tpd.purchase_no = tp.purchase_no " +
            "WHERE wspof.shouhin_code NOT IN ('00000021','00000023') " +
            "AND (tpd.goods_num IS DISTINCT FROM wspof.suuryou " +
            "OR tpd.goods_code IS DISTINCT FROM wspof.shouhin_code " +
            "OR wspof.denpyou_hizuke IS DISTINCT FROM tp.purchase_date " +
            "OR tpd.goods_name IS DISTINCT FROM wspof.shouhin_mei " +
            "OR tpd.goods_price IS DISTINCT FROM wspof.tanka " +
            "OR tpd.subtotal IS DISTINCT FROM wspof.kingaku " +
            "OR tpd.tax_rate IS DISTINCT FROM wspof.shouhizeiritsu " +
            "OR tpd.tax_type IS DISTINCT FROM wspof.kazei_kubun) " +
            "ORDER BY wspof.shop_no, wspof.shori_renban",
            countQuery = "SELECT COUNT(*) FROM w_smile_purchase_output_file wspof " +
                    "JOIN t_purchase_detail tpd ON wspof.shori_renban = tpd.ext_purchase_no " +
                    "AND wspof.gyou = tpd.purchase_detail_no " +
                    "AND tpd.shop_no = wspof.shop_no " +
                    "JOIN t_purchase tp ON tpd.purchase_no = tp.purchase_no " +
                    "WHERE wspof.shouhin_code NOT IN ('00000021','00000023') " +
                    "AND (tpd.goods_num IS DISTINCT FROM wspof.suuryou " +
                    "OR tpd.goods_code IS DISTINCT FROM wspof.shouhin_code " +
                    "OR wspof.denpyou_hizuke IS DISTINCT FROM tp.purchase_date " +
                    "OR tpd.goods_name IS DISTINCT FROM wspof.shouhin_mei " +
                    "OR tpd.goods_price IS DISTINCT FROM wspof.tanka " +
                    "OR tpd.subtotal IS DISTINCT FROM wspof.kingaku " +
                    "OR tpd.tax_rate IS DISTINCT FROM wspof.shouhizeiritsu " +
                    "OR tpd.tax_type IS DISTINCT FROM wspof.kazei_kubun) ",
            nativeQuery = true)
    Page<WSmilePurchaseOutputFile> findModifiedPurchases(Pageable pageable);


    List<WSmilePurchaseOutputFile> findByShopNoAndShoriRenban(int shopNo, long shorirenban);
}
