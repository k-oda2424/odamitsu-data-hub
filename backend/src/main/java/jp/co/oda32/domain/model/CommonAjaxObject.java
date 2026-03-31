package jp.co.oda32.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * Ajaxでメッセージのみ返したい場合に使う格納クラス
 *
 * @author k_oda
 * @since 2019/08/13
 */
@Data
@Builder
public class CommonAjaxObject {
    private String message;
}
