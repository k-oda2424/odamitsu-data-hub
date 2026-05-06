package jp.co.oda32.dto.finance;

import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MfEnumTranslationResponse {
    private Integer id;
    private String enumKind;
    private String englishCode;
    private String japaneseName;

    public static MfEnumTranslationResponse from(MMfEnumTranslation t) {
        return MfEnumTranslationResponse.builder()
                .id(t.getId())
                .enumKind(t.getEnumKind())
                .englishCode(t.getEnglishCode())
                .japaneseName(t.getJapaneseName())
                .build();
    }
}
