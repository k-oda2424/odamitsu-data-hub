package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MfTaxesResponse(
        @JsonProperty("taxes") @JsonAlias("data") List<MfTax> taxes
) {
    public List<MfTax> items() {
        return taxes != null ? taxes : List.of();
    }
}
