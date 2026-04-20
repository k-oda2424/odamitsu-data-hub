package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * MF /api/v3/accounts のレスポンスラッパー。
 * MF は通常 {data: [...]} 形式で返すが、未確認のため data フィールド null 時は空配列を返す。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfAccountsResponse(List<MfAccount> data) {
    public List<MfAccount> items() {
        return data != null ? data : List.of();
    }
}
