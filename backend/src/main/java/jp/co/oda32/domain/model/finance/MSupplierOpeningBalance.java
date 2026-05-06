package jp.co.oda32.domain.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jp.co.oda32.domain.model.embeddable.MSupplierOpeningBalancePK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * supplier 毎の前期繰越 (期首残)。
 * MF journal #1 の credit branch から抽出した opening balance を保持し、
 * buying ledger / integrity / supplier-balances で累積計算の初期値に使用する。
 * <p>
 * 設計書: claudedocs/design-supplier-opening-balance.md
 *
 * @since 2026-04-24
 */
@Entity
@Table(name = "m_supplier_opening_balance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MSupplierOpeningBalance {

    @EmbeddedId
    private MSupplierOpeningBalancePK pk;

    /** MF journal #1 から取得した税込残 (NULL = 未取得)。 */
    @Column(name = "mf_balance")
    private BigDecimal mfBalance;

    /** 手動補正額 (税込、signed)。 journal #1 未掲載や税理士確認差分用。 */
    @Column(name = "manual_adjustment", nullable = false)
    private BigDecimal manualAdjustment;

    /**
     * 合算値 = COALESCE(mf_balance, 0) + manual_adjustment。
     * DB の GENERATED ALWAYS AS STORED カラム。アプリから書き込み不可、読み取り専用。
     * <p>SF-G02: INSERT 直後だけでなく UPDATE 後も DB から再読込してメモリ上の値を最新化する。
     * これにより mfBalance / manualAdjustment 更新後に getEffectiveBalance() が stale 値を返さない。
     */
    @Column(name = "effective_balance", insertable = false, updatable = false)
    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    private BigDecimal effectiveBalance;

    /** 出典 MF journal 番号 (期首残高仕訳は通常 1)。 */
    @Column(name = "source_journal_number")
    private Integer sourceJournalNumber;

    /** MF 側の sub_account_name (creditSub)。再取得マッチング用。 */
    @Column(name = "source_sub_account_name", length = 200)
    private String sourceSubAccountName;

    /** 直近 MF 取得日時。 */
    @Column(name = "last_mf_fetched_at")
    private Instant lastMfFetchedAt;

    /** 手動補正理由。 */
    @Column(name = "adjustment_reason", length = 500)
    private String adjustmentReason;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "add_date_time", nullable = false, insertable = false, updatable = false)
    private Instant addDateTime;

    @Column(name = "add_user_no", nullable = false)
    private Integer addUserNo;

    @Column(name = "modify_date_time", nullable = false)
    private Instant modifyDateTime;

    @Column(name = "modify_user_no", nullable = false)
    private Integer modifyUserNo;

    @Column(name = "del_flg", nullable = false, length = 1)
    private String delFlg;
}
