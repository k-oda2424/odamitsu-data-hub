package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.embeddable.MSupplierOpeningBalancePK;
import jp.co.oda32.domain.model.finance.MSupplierOpeningBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * m_supplier_opening_balance の repository。
 *
 * @since 2026-04-24
 */
@Repository
public interface MSupplierOpeningBalanceRepository extends JpaRepository<MSupplierOpeningBalance, MSupplierOpeningBalancePK> {

    List<MSupplierOpeningBalance> findByPkShopNoAndPkOpeningDateAndDelFlg(
            Integer shopNo, LocalDate openingDate, String delFlg);

    List<MSupplierOpeningBalance> findByPkShopNoAndDelFlg(Integer shopNo, String delFlg);

    /**
     * SF-G06: del_flg を問わず (shop, openingDate) の全行を取得 (zombie 含む)。
     * fetchFromMfJournalOne の N+1 解消とゾンビ復活処理用。
     */
    List<MSupplierOpeningBalance> findByPkShopNoAndPkOpeningDate(
            Integer shopNo, LocalDate openingDate);
}
