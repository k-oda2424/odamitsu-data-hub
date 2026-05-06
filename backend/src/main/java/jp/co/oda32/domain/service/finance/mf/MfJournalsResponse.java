package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MfJournalsResponse(
        @JsonProperty("journals") List<MfJournal> journals
) {
    public List<MfJournal> items() {
        return journals != null ? journals : List.of();
    }
}
