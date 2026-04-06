package jp.co.oda32.dto.estimate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EstimateCreateRequest {
    @NotNull(message = "ショップ番号は必須です")
    private Integer shopNo;
    @NotNull(message = "得意先番号は必須です")
    private Integer partnerNo;
    private Integer destinationNo;
    @NotNull(message = "見積日は必須です")
    private LocalDate estimateDate;
    @NotNull(message = "価格改定日は必須です")
    private LocalDate priceChangeDate;
    private String note;
    @Valid
    @NotEmpty(message = "明細は1件以上必要です")
    private List<EstimateDetailCreateRequest> details;
}
