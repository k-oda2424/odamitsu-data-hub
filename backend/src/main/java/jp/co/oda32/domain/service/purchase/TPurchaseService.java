package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.repository.purchase.TPurchaseRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.purchase.TPurchaseSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.List;

/**
 * 仕入Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class TPurchaseService extends CustomService {
    private final TPurchaseRepository tPurchaseRepository;
    private final TPurchaseDetailService tPurchaseDetailService;
    private TPurchaseSpecification tPurchaseSpecification = new TPurchaseSpecification();

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public TPurchaseService(TPurchaseRepository tPurchaseRepository, TPurchaseDetailService tPurchaseDetailService) {
        this.tPurchaseRepository = tPurchaseRepository;
        this.tPurchaseDetailService = tPurchaseDetailService;
    }

    public List<TPurchase> findAll() {
        return tPurchaseRepository.findAll();
    }

    public TPurchase getByPK(Integer purchaseNo) {
        return this.tPurchaseRepository.findById(purchaseNo).orElseThrow();
    }

    /**
     * SMILEで削除された仕入を検出します。
     * w_smile_purchase_output_fileに存在する伝票日付（現在日付以前）を基準に、
     * SMILEから削除された仕入を検索します。
     *
     * @param shopNo ショップ番号（nullの場合は全てのショップが対象）
     * @return 削除された仕入のリスト
     */
    public List<TPurchase> findDeletedPurchases(Integer shopNo) {
        // 一時テーブルを使ってサブクエリの対象を作成
        StringBuilder sqlBuilder = new StringBuilder();

        // WITH句を使って効率的に日付とショップの組み合わせを取得
        sqlBuilder.append("WITH target_dates AS (");
        sqlBuilder.append("    SELECT DISTINCT denpyou_hizuke, shop_no");
        sqlBuilder.append("    FROM w_smile_purchase_output_file");
        sqlBuilder.append("    WHERE denpyou_hizuke <= CURRENT_DATE");
        sqlBuilder.append(")");

        // メインクエリ - JOINを使用して効率的にフィルタリング
        sqlBuilder.append("SELECT p.* FROM t_purchase p ");
        sqlBuilder.append("JOIN target_dates t ON p.purchase_date = t.denpyou_hizuke AND p.shop_no = t.shop_no ");
        sqlBuilder.append("WHERE p.del_flg = '0' ");
        sqlBuilder.append("AND p.ext_purchase_no IS NOT NULL ");
        sqlBuilder.append("AND p.purchase_date <= CURRENT_DATE ");

        // ショップ番号の条件を追加（指定があれば）
        if (shopNo != null) {
            sqlBuilder.append("AND p.shop_no = :shopNo ");
        }

        // NOT EXISTS部分 - CASTを使って文字列比較
        sqlBuilder.append("AND NOT EXISTS (");
        sqlBuilder.append("    SELECT 1 FROM w_smile_purchase_output_file w ");
        sqlBuilder.append("    WHERE w.shop_no = p.shop_no ");
        sqlBuilder.append("    AND CAST(w.shori_renban AS TEXT) = CAST(p.ext_purchase_no AS TEXT)");
        sqlBuilder.append(")");

        // NativeQueryとしてTPurchaseエンティティにマッピング
        Query query = entityManager.createNativeQuery(sqlBuilder.toString(), TPurchase.class);

        // パラメータバインド
        if (shopNo != null) {
            query.setParameter("shopNo", shopNo);
        }

        // 結果を返す
        @SuppressWarnings("unchecked")
        List<TPurchase> resultList = query.getResultList();
        return resultList;
    }

    /**
     * 指定された日付の仕入を取得します
     *
     * @param purchaseDate 仕入日
     * @return 指定日付の仕入リスト
     */
    public List<TPurchase> findByPurchaseDate(LocalDate purchaseDate) {
        return this.tPurchaseRepository.findAll(Specification
                .where(this.tPurchaseSpecification.purchaseDateContains(purchaseDate, purchaseDate))
                .and(this.tPurchaseSpecification.delFlgContains(Flag.NO)));
    }

    public List<TPurchase> find(Integer shopNo, Integer companyNo, List<Long> extPurchaseNoList, LocalDate purchaseDateFrom, LocalDate purchaseDateTo, Flag delFlg) {
        return this.tPurchaseRepository.findAll(Specification
                .where(this.tPurchaseSpecification.shopNoContains(shopNo))
                .and(this.tPurchaseSpecification.companyNoContains(companyNo))
                .and(this.tPurchaseSpecification.extPurchaseNoListContains(extPurchaseNoList))
                .and(this.tPurchaseSpecification.purchaseDateContains(purchaseDateFrom, purchaseDateTo))
                .and(this.tPurchaseSpecification.delFlgContains(delFlg)));
    }

    /**
     * 仕入を更新します。
     *
     * @param updatePurchase 仕入Entity
     * @return 更新した仕入Entity
     * @throws Exception システム例外
     */
    public TPurchase update(TPurchase updatePurchase) throws Exception {
        TPurchase tPurchase = this.getByPK(updatePurchase.getPurchaseNo());
        if (tPurchase == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の仕入番号が見つかりません。purchaseNo:%d", updatePurchase.getPurchaseNo()));
        }
        return this.update(this.tPurchaseRepository, updatePurchase);
    }

    /**
     * 仕入を登録します。
     *
     * @param tPurchase 仕入Entity
     * @return 登録した仕入Entity
     */
    public TPurchase insert(TPurchase tPurchase) throws Exception {
        return this.insert(this.tPurchaseRepository, tPurchase);
    }

    public void delete(TPurchase tPurchase) {
        this.tPurchaseRepository.delete(tPurchase);
        this.tPurchaseDetailService.deleteDetailList(tPurchase.getPurchaseNo());
    }

    /**
     * shop_noとprocessing_serial_numberのユニークキーで検索します。
     *
     * @param shopNo                 ショップ番号
     * @param processingSerialNumber smile処理連番
     * @return 注文情報
     */
    public TPurchase getByUniqKey(int shopNo, long processingSerialNumber) {
        return this.tPurchaseRepository.getByShopNoAndExtPurchaseNo(shopNo, processingSerialNumber);
    }

    public List<TPurchase> findByPurchaseNoList(List<Integer> purchaseNoList) {
        return this.tPurchaseRepository.findAll(Specification
                .where(this.tPurchaseSpecification.purchaseNoListContains(purchaseNoList)));
    }

    /**
     * 仕入を物理的に削除します。
     * 関連する仕入明細も削除する場合は注意が必要です。
     *
     * @param purchase 仕入Entity
     * @throws Exception 削除エラー時
     */
    public void deletePermanently(TPurchase purchase) throws Exception {
        this.deletePermanently(this.tPurchaseRepository, purchase);
    }

    /**
     * 仕入番号が指定された仕入を取得します
     *
     * @param purchaseNo 仕入番号
     * @return 仕入Entity
     */
    public TPurchase getByPurchaseNo(Integer purchaseNo) {
        return this.tPurchaseRepository.findById(purchaseNo).orElse(null);
    }
}