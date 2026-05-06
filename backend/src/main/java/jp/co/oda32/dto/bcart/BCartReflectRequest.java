package jp.co.oda32.dto.bcart;

import lombok.Data;

import java.util.List;

/**
 * B-CART商品セット変更点の一括反映リクエスト。
 *
 * `productSetIds` を指定するか、`all=true` で全件対象（最大 200 件）。
 */
@Data
public class BCartReflectRequest {
    private List<Long> productSetIds;
    private Boolean all;
}
