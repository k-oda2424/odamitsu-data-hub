package jp.co.oda32.config;

import jp.co.oda32.constant.BcartShipmentStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * クエリパラメータから {@link BcartShipmentStatus} への変換。
 * displayName (例: "未発送") と enum 名 (例: "NOT_SHIPPED") の両方を受け付ける。
 * Spring MVC が {@code @RequestParam BcartShipmentStatus} バインド時に自動使用する。
 */
@Component
public class BcartShipmentStatusConverter implements Converter<String, BcartShipmentStatus> {

    @Override
    public BcartShipmentStatus convert(String source) {
        if (source == null || source.isBlank()) return null;
        BcartShipmentStatus byDisplay = BcartShipmentStatus.parse(source);
        if (byDisplay != null) return byDisplay;
        try {
            return BcartShipmentStatus.valueOf(source);
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }
}
