package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InvoiceImportResult {
    private String closingDate;
    private int shopNo;
    private int totalRows;
    private int insertedRows;
    private int updatedRows;
    private int skippedRows;
    private List<String> errors;
}
