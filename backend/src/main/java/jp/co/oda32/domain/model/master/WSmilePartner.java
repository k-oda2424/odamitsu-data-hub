package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.embeddable.WSmilePartnerPK;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "w_smile_partner")
@IdClass(WSmilePartnerPK.class)
public class WSmilePartner {
    @Id
    @Column(name = "得意先コード")
    private String 得意先コード;

    @Id
    @Column(name = "shop_no")
    private int shopNo;

    private String 得意先名1;
    private String 得意先名2;
    private String 得意先名略称;
    private String 得意先名索引;
    private String 郵便番号;
    private String 住所1;
    private String 住所2;
    private String 住所3;
    private String カスタマバーコード;
    private String 電話番号;
    private String FAX番号;
    private String 営業所コード;
    private String 営業所名;
    private String 部門コード;
    private String 部門名;
    private String 地区コード;
    private String 地区名;
    private String 業種コード;
    private String 業種名;
    private String グループコード;
    private String グループ名;
    private String 単価ランクコード;
    private String 単価ランク名;
    private String 得意先分類6コード;
    private String 得意先分類6名;
    private String 得意先分類7コード;
    private String 得意先分類7名;
    private String 得意先分類8コード;
    private String 得意先分類8名;
    private String 得意先分類9コード;
    private String 得意先分類9名;
    private String 請求先コード;
    private String 請求先名;
    private int 請求先区分;
    private String 請求先区分名;
    private String 請求先営業所コード;
    private String 請求先営業所名;
    private String 請求先部門コード;
    private String 請求先部門名;
    private String 請求先地区コード;
    private String 請求先地区名;
    private String 請求先業種コード;
    private String 請求先業種名;
    private String 請求先グループコード;
    private String 請求先グループ名;
    private String 請求先単価ランクコード;
    private String 請求先単価ランク名;
    private String 請求先分類6コード;
    private String 請求先分類6名;
    private String 請求先分類7コード;
    private String 請求先分類7名;
    private String 請求先分類8コード;
    private String 請求先分類8名;
    private String 請求先分類9コード;
    private String 請求先分類9名;
    private String 担当者コード;
    private String 担当者名;
    private String 担当者分類0コード;
    private String 担当者分類0名;
    private String 担当者分類1コード;
    private String 担当者分類1名;
    private String 担当者分類2コード;
    private String 担当者分類2名;
    private String 担当者分類3コード;
    private String 担当者分類3名;
    private String 担当者分類4コード;
    private String 担当者分類4名;
    private String 担当者分類5コード;
    private String 担当者分類5名;
    private String 担当者分類6コード;
    private String 担当者分類6名;
    private String 担当者分類7コード;
    private String 担当者分類7名;
    private String 担当者分類8コード;
    private String 担当者分類8名;
    private String 担当者分類9コード;
    private String 担当者分類9名;
    private int 締日1;
    private int 締日2;
    private int 締日3;
    private int 入金日1;
    private int 入金日2;
    private int 入金日3;
    private int 入金サイクル1;
    private String 入金サイクル名1;
    private int 入金サイクル2;
    private String 入金サイクル名2;
    private int 入金サイクル3;
    private String 入金サイクル名3;
    private int 入金条件1;
    private String 入金条件名1;
    private int 入金条件2;
    private String 入金条件名2;
    private int 入金条件3;
    private String 入金条件名3;
    private BigDecimal 与信限度額;
    private int 単価掛率区分;
    private String 単価掛率区分名;
    private BigDecimal 単価掛率;
    private int 単価ランク;
    private int 単価処理区分;
    private String 単価処理区分名;
    private BigDecimal 単価処理単位;
    private int 金額処理区分;
    private String 金額処理区分名;
    private int 課税対象区分;
    private String 課税対象区分名;
    private int 売上単価設定区分;
    private String 売上単価設定区分名;
    private int 上代単価設定区分;
    private String 上代単価設定区分名;
    private int 単価変換区分;
    private String 単価変換区分名;
    private int 消費税通知区分;
    private String 消費税通知区分名;
    private int 消費税計算区分;
    private String 消費税計算区分名;
    private BigDecimal 消費税計算単位;
    private int 消費税分解区分;
    private String 消費税分解区分名;
    private int 請求書出力タイプ;
    private String 請求書出力タイプ名;
    private int 請求書出力形式;
    private String 請求書出力形式名;
    private int 得意先台帳出力形式;
    private String 得意先台帳出力形式名;
    private int 請求消費税算出単位;
    private String 請求消費税算出単位名;
    private BigDecimal 期首売掛残高;
    private BigDecimal 前回請求残高;
    private String 相殺仕入先コード;
    private String 相殺仕入先名;
    private int 日付印字区分;
    private String 日付印字区分名;
    private String 相手先担当者名;
    private String 取引;
    private String 取引名;
    private String 売上伝票会社名パターン;
    private String 売上伝票会社名名称;
    private String 見積書会社名パターン;
    private String 見積書会社名名称;
    private String 請求書会社名パターン;
    private String 請求書会社名名称;
    private int マスター検索表示区分;
    private String マスター検索表示区分名;
    private int 伝票出力区分;
    private String 伝票出力区分名;
    private int 変換コード伝票出力区分;
    private String 変換コード伝票出力区分名;
    private int 変換コード見積書出力区分;
    private String 変換コード見積書出力区分名;
    private int 変換コード請求書出力区分;
    private String 変換コード請求書出力区分名;
    private int 入力処理モード;
    private String 入力処理モード名;
    private int 個別設定入力行数;
    private String 社店コード;
    private String 分類コード;
    private Integer 伝票区分;
    private String 取引先コード;
    private String 有効期間開始日;
    private String 有効期間終了日;
    private String 操作日付;
    private String ログインID;
    private String ログイン名;
    private int 登録番号売上伝票出力;
    private String 登録番号売上伝票出力名;
    private int 登録番号請求書出力;
    private String 登録番号請求書出力名;
    private int 自社負担手数料入金後売上値引;
    private String 自社負担手数料入金後売上値引名;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WSmilePartner that = (WSmilePartner) o;
        return shopNo == that.shopNo && Objects.equals(得意先コード, that.得意先コード);
    }

    @Override
    public int hashCode() {
        return Objects.hash(得意先コード, shopNo);
    }
}
