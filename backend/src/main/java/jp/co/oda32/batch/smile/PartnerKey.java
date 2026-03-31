package jp.co.oda32.batch.smile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PartnerKey {
    private Integer shopNo;
    private String partnerCode;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartnerKey that = (PartnerKey) o;
        return Objects.equals(this.shopNo, that.shopNo) &&
                Objects.equals(partnerCode, that.partnerCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shopNo, partnerCode);
    }
}