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
public class SalesGoodsKey {
    private Integer shopNo;
    private String goodsCode;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SalesGoodsKey that = (SalesGoodsKey) o;
        return Objects.equals(shopNo, that.shopNo) &&
                Objects.equals(goodsCode, that.goodsCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shopNo, goodsCode);
    }
}