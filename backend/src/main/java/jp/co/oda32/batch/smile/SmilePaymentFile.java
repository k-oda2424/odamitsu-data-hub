package jp.co.oda32.batch.smile;

import jp.co.oda32.util.DateTimeUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * SMILE支払情報ファイルのエンティティクラス
 * SMILEからの取り込み
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SmilePaymentFile {
    String 伝票日付;
    String 年月度;
    String 伝票番号;
    String 処理連番;
    String 行;
    String 仕入先コード;
    String 仕入先名１;
    String 仕入先名２;
    String 仕入先名略称;
    String 営業所コード;
    String 営業所名;
    String 部門コード;
    String 部門名;
    String 地区コード;
    String 地区名;
    String 業種コード;
    String 業種名;
    String 仕入先分類４コード;
    String 仕入先分類４名;
    String 仕入先分類５コード;
    String 仕入先分類５名;
    String 仕入先分類６コード;
    String 仕入先分類６名;
    String 仕入先分類７コード;
    String 仕入先分類７名;
    String 仕入先分類８コード;
    String 仕入先分類８名;
    String 仕入先分類９コード;
    String 仕入先分類９名;
    String 取引区分;
    String 取引区分名;
    String 取引区分属性;
    String 取引区分属性名;
    String 支払額;
    String 決済予定日;
    String 備考コード;
    String 備考;
    String ログインID;
    String ログイン名;
    String 操作日付;
    String データ発生区分;
    String 相手処理連番;
    String チェックマーク区分;
    String チェックマーク区分名;

    public static String[] getSmilePaymentFileFormat() {
        return new String[]{
                "伝票日付",
                "年月度",
                "伝票番号",
                "処理連番",
                "行",
                "仕入先コード",
                "仕入先名１",
                "仕入先名２",
                "仕入先名略称",
                "営業所コード",
                "営業所名",
                "部門コード",
                "部門名",
                "地区コード",
                "地区名",
                "業種コード",
                "業種名",
                "仕入先分類４コード",
                "仕入先分類４名",
                "仕入先分類５コード",
                "仕入先分類５名",
                "仕入先分類６コード",
                "仕入先分類６名",
                "仕入先分類７コード",
                "仕入先分類７名",
                "仕入先分類８コード",
                "仕入先分類８名",
                "仕入先分類９コード",
                "仕入先分類９名",
                "取引区分",
                "取引区分名",
                "取引区分属性",
                "取引区分属性名",
                "支払額",
                "決済予定日",
                "備考コード",
                "備考",
                "ログインID",
                "ログイン名",
                "操作日付",
                "データ発生区分",
                "相手処理連番",
                "チェックマーク区分",
                "チェックマーク区分名"
        };
    }

    public LocalDate get伝票日付() {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
            return DateTimeUtil.stringToLocalDate(this.伝票日付, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    public Long get処理連番() {
        try {
            return Long.parseLong(this.処理連番);
        } catch (Exception e) {
            return null;
        }
    }

    public Integer get行() {
        try {
            return Integer.parseInt(this.行);
        } catch (Exception e) {
            return null;
        }
    }

    public BigDecimal get支払額() {
        try {
            return new BigDecimal(this.支払額);
        } catch (Exception e) {
            return null;
        }
    }

    public LocalDate get決済予定日() {
        try {
            if (this.決済予定日 == null || this.決済予定日.trim().isEmpty() || this.決済予定日.equals("0")) {
                return null;
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
            return DateTimeUtil.stringToLocalDate(this.決済予定日, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    public LocalDate get操作日付() {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
            return DateTimeUtil.stringToLocalDate(this.操作日付, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    public Long get相手処理連番() {
        try {
            return Long.parseLong(this.相手処理連番);
        } catch (Exception e) {
            return null;
        }
    }
}
