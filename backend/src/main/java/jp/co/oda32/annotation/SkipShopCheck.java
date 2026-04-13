package jp.co.oda32.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ShopCheckAop によるショップ権限自動フィルタをスキップする。
 * マスタ参照や全店舗横断で結果を返したいメソッドに付与する。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipShopCheck {
}
