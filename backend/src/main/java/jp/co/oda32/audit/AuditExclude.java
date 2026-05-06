package jp.co.oda32.audit;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * T2: 監査ログ JSON シリアライズから除外するフィールド。
 * <p>
 * トークン文字列・暗号化済み秘密情報・大きい blob (CSV 本体, Excel バイト列) などに付与すると
 * {@link FinanceAuditAspect} が JSONB 書き込み時に値を出力しない。
 * <p>
 * 内部的には Jackson の {@link JsonIgnore} を再エクスポートしているため、
 * 通常の REST レスポンス用 ObjectMapper でも値が外れる点に留意 (今回は finance Entity に
 * 直接付けるため、API 経由でも返却されない -- これは意図した挙動)。
 *
 * @since 2026-05-04 (T2)
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonIgnore
public @interface AuditExclude {}
