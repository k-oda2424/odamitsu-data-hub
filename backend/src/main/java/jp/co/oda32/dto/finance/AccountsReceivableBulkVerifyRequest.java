package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 売掛金の一括検証リクエスト（画面の検索条件範囲を対象）。
 */
@Data
public class AccountsReceivableBulkVerifyRequest {
    private Integer shopNo;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
}
