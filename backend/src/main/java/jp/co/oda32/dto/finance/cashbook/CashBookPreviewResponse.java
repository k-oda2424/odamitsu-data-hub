package jp.co.oda32.dto.finance.cashbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CashBookPreviewResponse {
    private String uploadId;
    private String fileName;
    private int totalRows;
    private int errorCount;
    private List<CashBookPreviewRow> rows;
    private List<String> unmappedClients;
    private List<String> unknownDescriptions;
}
