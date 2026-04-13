package jp.co.oda32.dto.comparison;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ComparisonCreateRequest {
    @NotNull
    private Integer shopNo;
    private Integer partnerNo;
    private Integer destinationNo;
    @NotNull
    private LocalDate comparisonDate;
    private String title;
    private String note;
    @NotEmpty
    @Valid
    private List<ComparisonGroupCreateRequest> groups;
}
