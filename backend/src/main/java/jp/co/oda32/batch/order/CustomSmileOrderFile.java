package jp.co.oda32.batch.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 取り込み用注文ファイルフォーマット
 * SMILEからの取り込み
 * SMILEの項目はすべてローマ字で定義する
 *
 * @author k_oda
 * @since 2021/08/05
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CustomSmileOrderFile {
    Integer shopNo;
}
