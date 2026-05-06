package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * MF /api/v3/accounts のレスポンスラッパー。
 * 実レスポンスは {@code { "accounts": [...] }} 形式。将来的な変更に備え
 * {@code data} にも JsonAlias を付けて両対応。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfAccountsResponse(
        @JsonProperty("accounts") @JsonAlias("data") List<MfAccount> accounts
) {
    public List<MfAccount> items() {
        return accounts != null ? accounts : List.of();
    }
}
