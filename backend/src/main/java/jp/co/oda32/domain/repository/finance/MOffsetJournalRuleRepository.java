package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * G2-M8: {@link MOffsetJournalRule} の Repository。
 *
 * <p>shop_no + del_flg='0' の UNIQUE 制約があるため、active 行は最大 1 件。
 * lookup は {@link #findByShopNoAndDelFlg(Integer, String)} を使う。
 */
@Repository
public interface MOffsetJournalRuleRepository extends JpaRepository<MOffsetJournalRule, Integer> {

    /** shop_no + del_flg で active 行 (高々 1 件) を取得。 */
    Optional<MOffsetJournalRule> findByShopNoAndDelFlg(Integer shopNo, String delFlg);

    /** Admin 一覧画面用: del_flg='0' を shop_no 昇順で全件取得。 */
    List<MOffsetJournalRule> findByDelFlgOrderByShopNoAsc(String delFlg);

    /**
     * Codex Major fix: shop_no 別 active 行件数。
     * delete() での「最後の active 行は削除禁止」バリデーションに使用する。
     */
    long countByShopNoAndDelFlg(Integer shopNo, String delFlg);
}
