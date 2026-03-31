package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.TPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 仕入(t_purchase)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/06/02
 */
public interface TPurchaseRepository extends JpaRepository<TPurchase, Integer>, JpaSpecificationExecutor<TPurchase> {
    /**
     * 指定されたショップ番号、外部購入番号の仕入を取得する
     *
     * @param shopNo        ショップ番号
     * @param extPurchaseNo 外部購入番号（SMILE処理連番）
     * @return 検索結果
     */
    TPurchase getByShopNoAndExtPurchaseNo(Integer shopNo, Long extPurchaseNo);
}