package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import jp.co.oda32.audit.AuditExclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_payment_mf_import_history")
public class TPaymentMfImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "csv_filename", nullable = false)
    private String csvFilename;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "matched_count", nullable = false)
    @Builder.Default
    private Integer matchedCount = 0;

    @Column(name = "diff_count", nullable = false)
    @Builder.Default
    private Integer diffCount = 0;

    @Column(name = "unmatched_count", nullable = false)
    @Builder.Default
    private Integer unmatchedCount = 0;

    @AuditExclude
    @Column(name = "csv_body")
    private byte[] csvBody;

    /** P1-08: 取込元 Excel の SHA-256 (hex)。同一 hash 再取込時に preview で L1 警告。 */
    @Column(name = "source_file_hash", length = 64)
    private String sourceFileHash;

    /** P1-08: applyVerification 実行タイムスタンプ。NULL=未確定。non-NULL=確定済 (L2 警告対象)。 */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /** P1-08: applyVerification 実行ユーザー (m_user.user_no)。 */
    @Column(name = "applied_by_user_no")
    private Integer appliedByUserNo;

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
