package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MfTax(
        String id,
        String name,              // "対象外" / "課税売上 10%" 等
        String abbreviation,      // "課売 10%" 等
        Boolean available,
        @JsonProperty("search_key") String searchKey,
        @JsonProperty("tax_rate") BigDecimal taxRate
) {}
