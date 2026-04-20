package jp.co.oda32.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 買掛→仕入仕訳 CSV 出力のプレビュー情報。
 * 買掛金一覧画面の「仕入仕訳CSV出力（MF）」ダイアログで件数確認用に表示する。
 */
@Data
@Builder
@AllArgsConstructor
public class PurchaseJournalExportPreviewResponse {
    private LocalDate transactionMonth;
    /** supplier × taxRate で集約後の行数。 */
    private int rowCount;
    /** 出力対象（集約前）の買掛金サマリ件数。 */
    private int payableCount;
    /** 集約後の税込合計金額。 */
    private BigDecimal totalAmount;
    /** mfExportEnabled=false で除外された件数（集約前）。 */
    private long nonExportableCount;
    /** MF勘定科目マスタ未登録で CSV から除外される supplier 一覧。 */
    private List<String> skippedSuppliers;
}
