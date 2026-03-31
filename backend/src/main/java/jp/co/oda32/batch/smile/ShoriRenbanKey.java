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
public class ShoriRenbanKey {
    private Integer shopNo;
    private long shorirenban;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShoriRenbanKey that = (ShoriRenbanKey) o;
        return Objects.equals(shopNo, that.shopNo) &&
                Objects.equals(shorirenban, that.shorirenban);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shopNo, shorirenban);
    }
}