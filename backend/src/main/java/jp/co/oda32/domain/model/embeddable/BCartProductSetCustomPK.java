package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * BCartProductSetCustomテーブルのキー
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BCartProductSetCustomPK implements Serializable {
    private Long id;
    private Long productSetId;
    private Long orderId;
}
