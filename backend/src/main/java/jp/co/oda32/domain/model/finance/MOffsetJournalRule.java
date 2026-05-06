package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * G2-M8: PaymentMfImport の OFFSET 副行 (PAYABLE_OFFSET / DIRECT_PURCHASE_OFFSET) の
 * 貸方科目を管理するマスタ。
 *
 * <p>従来 {@code PaymentMfImportService} にハードコードされていた
 * 「仕入値引・戻し高 / 物販事業部 / 課税仕入-返還等 10%」を本テーブルから lookup し、
 * 税理士確認結果に応じて admin が UI から書き換え可能とする。
 *
 * <p>運用: 1 shop につき 1 行 (V041 seed で shop_no=1 のデフォルト値を投入)。
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_offset_journal_rule")
public class MOffsetJournalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    @Column(name = "credit_account", nullable = false, length = 100)
    private String creditAccount;

    @Column(name = "credit_sub_account", length = 100)
    private String creditSubAccount;

    @Column(name = "credit_department", length = 100)
    private String creditDepartment;

    @Column(name = "credit_tax_category", nullable = false, length = 100)
    private String creditTaxCategory;

    @Column(name = "summary_prefix", nullable = false, length = 100)
    @Builder.Default
    private String summaryPrefix = "相殺／";

    @Column(name = "del_flg", nullable = false, length = 1)
    @Builder.Default
    private String delFlg = "0";

    @Column(name = "add_date_time")
    private LocalDateTime addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private LocalDateTime modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
}
