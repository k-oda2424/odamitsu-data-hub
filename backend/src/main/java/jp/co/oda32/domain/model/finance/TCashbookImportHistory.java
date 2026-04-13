package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_cashbook_import_history")
public class TCashbookImportHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "period_label", nullable = false, unique = true)
    private String periodLabel;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "processed_at", nullable = false)
    private Timestamp processedAt;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "total_income", nullable = false)
    private int totalIncome;

    @Column(name = "total_payment", nullable = false)
    private int totalPayment;

    @Column(name = "csv_content", columnDefinition = "TEXT")
    private String csvContent;
}
