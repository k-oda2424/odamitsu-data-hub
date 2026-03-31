package jp.co.oda32.domain.model.smile;

import jp.co.oda32.batch.purchase.ExtPurchaseFile;
import jp.co.oda32.domain.model.embeddable.WSmilePurchaseOutputFilePK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Smileから出力される仕入データファイルに対応したテーブルのEntity
 *
 * @author k_oda
 * @since 2024/05/08
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "w_smile_purchase_output_file")
@IdClass(WSmilePurchaseOutputFilePK.class)
public class WSmilePurchaseOutputFile implements ISmileGoodsFile {

    @Id
    @Column(name = "shori_renban")
    private Long shoriRenban; // 処理連番

    @Id
    @Column(name = "gyou")
    private Integer gyou; // 行

    @Id
    @Column(name = "shop_no")
    private Integer shopNo; // ショップ番号

    @Id
    @Column(name = "meisaikubun")
    private int meisaikubun; // 明細区分

    @Column(name = "company_no")
    private Integer companyNo; // 仕入会社番号（小田光旧事業部）

    @Column(name = "denpyou_hizuke")
    private LocalDate denpyouHizuke; // 伝票日付

    @Column(name = "nengetsudo")
    private String nengetsudo; // 年月度

    @Column(name = "denpyou_bangou")
    private BigDecimal denpyouBangou; // 伝票番号

    @Column(name = "meisaikubun_mei")
    private String meisaikubunMei; // 明細区分名

    @Column(name = "shiiresaki_code")
    private String shiiresakiCode; // 仕入先コード

    @Column(name = "shiiresaki_mei1")
    private String shiiresakiMei1; // 仕入先名1

    @Column(name = "shiiresaki_mei2")
    private String shiiresakiMei2; // 仕入先名2

    @Column(name = "shiiresaki_ryakushou")
    private String shiiresakiRyakushou; // 仕入先名略称

    @Column(name = "shiiresaki_eigyosho_code")
    private String shiiresakiEigyoshoCode; // 仕入先営業所コード

    @Column(name = "shiiresaki_eigyosho_mei")
    private String shiiresakiEigyoshoMei; // 仕入先営業所名

    @Column(name = "shiiresaki_bumon_code")
    private String shiiresakiBumonCode; // 仕入先部門コード

    @Column(name = "shiiresaki_bumon_mei")
    private String shiiresakiBumonMei; // 仕入先部門名

    @Column(name = "shiiresaki_chiku_code")
    private String shiiresakiChikuCode; // 仕入先地区コード

    @Column(name = "shiiresaki_chiku_mei")
    private String shiiresakiChikuMei; // 仕入先地区名

    @Column(name = "shiiresaki_gyoushu_code")
    private String shiiresakiGyoushuCode; // 仕入先業種コード

    @Column(name = "shiiresaki_gyoushu_mei")
    private String shiiresakiGyoushuMei; // 仕入先業種名
    @Column(name = "shiiresaki_bunrui4_code")
    private String shiiresakiBunrui4Code; // 仕入先分類４コード

    @Column(name = "shiiresaki_bunrui4_mei")
    private String shiiresakiBunrui4Mei; // 仕入先分類４名

    @Column(name = "shiiresaki_bunrui5_code")
    private String shiiresakiBunrui5Code; // 仕入先分類５コード

    @Column(name = "shiiresaki_bunrui5_mei")
    private String shiiresakiBunrui5Mei; // 仕入先分類５名

    @Column(name = "shiiresaki_bunrui6_code")
    private String shiiresakiBunrui6Code; // 仕入先分類６コード

    @Column(name = "shiiresaki_bunrui6_mei")
    private String shiiresakiBunrui6Mei; // 仕入先分類６名

    @Column(name = "shiiresaki_bunrui7_code")
    private String shiiresakiBunrui7Code; // 仕入先分類７コード

    @Column(name = "shiiresaki_bunrui7_mei")
    private String shiiresakiBunrui7Mei; // 仕入先分類７名

    @Column(name = "shiiresaki_bunrui8_code")
    private String shiiresakiBunrui8Code; // 仕入先分類８コード

    @Column(name = "shiiresaki_bunrui8_mei")
    private String shiiresakiBunrui8Mei; // 仕入先分類８名

    @Column(name = "shiiresaki_bunrui9_code")
    private String shiiresakiBunrui9Code; // 仕入先分類９コード

    @Column(name = "shiiresaki_bunrui9_mei")
    private String shiiresakiBunrui9Mei; // 仕入先分類９名
    @Column(name = "shiharaisaki_code")
    private String shiharaisakiCode; // 支払先コード

    @Column(name = "shiharaisaki_mei")
    private String shiharaisakiMei; // 支払先名

    @Column(name = "shiharaisaki_eigyosho_code")
    private String shiharaisakiEigyoshoCode; // 支払先営業所コード

    @Column(name = "shiharaisaki_eigyosho_mei")
    private String shiharaisakiEigyoshoMei; // 支払先営業所名

    @Column(name = "shiharaisaki_bumon_code")
    private String shiharaisakiBumonCode; // 支払先部門コード

    @Column(name = "shiharaisaki_bumon_mei")
    private String shiharaisakiBumonMei; // 支払先部門名

    @Column(name = "shiharaisaki_chiku_code")
    private String shiharaisakiChikuCode; // 支払先地区コード

    @Column(name = "shiharaisaki_chiku_mei")
    private String shiharaisakiChikuMei; // 支払先地区名

    @Column(name = "shiharaisaki_gyoushu_code")
    private String shiharaisakiGyoushuCode; // 支払先業種コード

    @Column(name = "shiharaisaki_gyoushu_mei")
    private String shiharaisakiGyoushuMei; // 支払先業種名
    @Column(name = "tantousha_code")
    private String tantoushaCode; // 担当者コード

    @Column(name = "tantousha_mei")
    private String tantoushaMei; // 担当者名

    @Column(name = "tantousha_bunrui0_code")
    private String tantoushaBunrui0Code; // 担当者分類０コード

    @Column(name = "tantousha_bunrui0_mei")
    private String tantoushaBunrui0Mei; // 担当者分類０名

    @Column(name = "tantousha_bunrui1_code")
    private String tantoushaBunrui1Code; // 担当者分類１コード

    @Column(name = "tantousha_bunrui1_mei")
    private String tantoushaBunrui1Mei; // 担当者分類１名

    @Column(name = "tantousha_bunrui2_code")
    private String tantoushaBunrui2Code; // 担当者分類２コード

    @Column(name = "tantousha_bunrui2_mei")
    private String tantoushaBunrui2Mei; // 担当者分類２名

    @Column(name = "tantousha_bunrui3_code")
    private String tantoushaBunrui3Code; // 担当者分類３コード

    @Column(name = "tantousha_bunrui3_mei")
    private String tantoushaBunrui3Mei; // 担当者分類３名

    @Column(name = "tantousha_bunrui4_code")
    private String tantoushaBunrui4Code; // 担当者分類４コード

    @Column(name = "tantousha_bunrui4_mei")
    private String tantoushaBunrui4Mei; // 担当者分類４名

    @Column(name = "tantousha_bunrui5_code")
    private String tantoushaBunrui5Code; // 担当者分類５コード

    @Column(name = "tantousha_bunrui5_mei")
    private String tantoushaBunrui5Mei; // 担当者分類５名

    @Column(name = "tantousha_bunrui6_code")
    private String tantoushaBunrui6Code; // 担当者分類６コード

    @Column(name = "tantousha_bunrui6_mei")
    private String tantoushaBunrui6Mei; // 担当者分類６名

    @Column(name = "tantousha_bunrui7_code")
    private String tantoushaBunrui7Code; // 担当者分類７コード

    @Column(name = "tantousha_bunrui7_mei")
    private String tantoushaBunrui7Mei; // 担当者分類７名

    @Column(name = "tantousha_bunrui8_code")
    private String tantoushaBunrui8Code; // 担当者分類８コード

    @Column(name = "tantousha_bunrui8_mei")
    private String tantoushaBunrui8Mei; // 担当者分類８名

    @Column(name = "tantousha_bunrui9_code")
    private String tantoushaBunrui9Code; // 担当者分類９コード

    @Column(name = "tantousha_bunrui9_mei")
    private String tantoushaBunrui9Mei; // 担当者分類９名

    @Column(name = "shiharai")
    private BigDecimal shiharai; // 支払

    @Column(name = "shiharai_kubun_mei")
    private String shiharaiKubunMei; // 支払区分名

    @Column(name = "kaikake_kubun")
    private BigDecimal kaikakeKubun; // 買掛区分

    @Column(name = "kaikake_kubun_mei")
    private String kaikakeKubunMei; // 買掛区分名

    @Column(name = "torihiki_kubun")
    private BigDecimal torihikiKubun; // 取引区分

    @Column(name = "torihiki_kubun_mei")
    private String torihikiKubunMei; // 取引区分名

    @Column(name = "torihiki_kubun_zokusei")
    private BigDecimal torihikiKubunZokusei; // 取引区分属性

    @Column(name = "torihiki_kubun_zokusei_mei")
    private String torihikiKubunZokuseiMei; // 取引区分属性名
    @Column(name = "shouhin_code")
    private String shouhinCode; // 商品コード

    @Column(name = "shouhin_mei")
    private String shouhinMei; // 商品名

    @Column(name = "maker_code")
    private String makerCode; // メーカーコード

    @Column(name = "maker_mei")
    private String makerMei; // メーカー名

    @Column(name = "shouhin_bunrui_code")
    private String shouhinBunruiCode; // 商品分類コード

    @Column(name = "shouhin_bunrui_mei")
    private String shouhinBunruiMei; // 商品分類名
    @Column(name = "irisu")
    private BigDecimal irisu; // 入数

    @Column(name = "kosuu")
    private BigDecimal kosuu; // 個数
    @Column(name = "kosuu_tanni")
    private String kosuuTanni; // 個数単位

    @Column(name = "suuryou")
    private BigDecimal suuryou; // 数量

    @Column(name = "suuryou_tanni")
    private String suuryouTanni; // 数量単位

    @Column(name = "tanka")
    private BigDecimal tanka; // 単価

    @Column(name = "kingaku")
    private BigDecimal kingaku; // 金額

    @Column(name = "tanka_kakuritsu")
    private BigDecimal tankaKakuritsu; // 単価掛率

    @Column(name = "kazei_kubun")
    private String kazeiKubun; // 課税区分

    @Column(name = "kazei_kubun_mei")
    private String kazeiKubunMei; // 課税区分名

    @Column(name = "shouhizeiritsu")
    private BigDecimal shouhizeiritsu; // 消費税率

    @Column(name = "uchishouhizei")
    private BigDecimal uchishouhizei; // 内消費税
    @Column(name = "gyou_tekiyou_code")

    private String gyouTekiyouCode; // 行摘要コード

    @Column(name = "gyou_tekiyou1")
    private String gyouTekiyou1; // 行摘要1

    @Column(name = "gyou_tekiyou2")
    private String gyouTekiyou2; // 行摘要2

    @Column(name = "biko_code")
    private String bikoCode; // 備考コード

    @Column(name = "biko")
    private String biko; // 備考

    @Column(name = "login_id")
    private String loginId; // ログインID

    @Column(name = "login_mei")
    private String loginMei; // ログイン名

    @Column(name = "sousa_hizuke")
    private String sousaHizuke; // 操作日付

    @Column(name = "hacchuu_bangou")
    private BigDecimal hacchuuBangou; // 発注番号

    @Column(name = "hacchuu_gyou")
    private BigDecimal hacchuuGyou; // 発注行

    @Column(name = "order_no")
    private String orderNo; // オーダー番号

    @Column(name = "jidou_seisei_kubun")
    private BigDecimal jidouSeiseiKubun; // 自動生成区分

    @Column(name = "jidou_seisei_kubun_mei")
    private String jidouSeiseiKubunMei; // 自動生成区分名

    @Column(name = "denpyou_shouhizei_keisan_kubun")
    private BigDecimal denpyouShouhizeiKeisanKubun; // 伝票消費税計算区分

    @Column(name = "denpyou_shouhizei_keisan_kubun_mei")
    private String denpyouShouhizeiKeisanKubunMei; // 伝票消費税計算区分名

    @Column(name = "data_hassei_kubun")
    private BigDecimal dataHasseiKubun; // データ発生区分

    @Column(name = "aite_shori_renban")
    private BigDecimal aiteShoriRenban; // 相手処理連番

    @Column(name = "nyuuryoku_pattern_no")
    private String nyuuryokuPatternNo; // 入力パターンNo

    @Column(name = "nyuuryoku_pattern_mei")
    private String nyuuryokuPatternMei; // 入力パターン名

    @Column(name = "checkmark_kubun")
    private BigDecimal checkmarkKubun; // チェックマーク区分

    @Column(name = "checkmark_kubun_mei")
    private String checkmarkKubunMei; // チェックマーク区分名

    @Column(name = "shouhizei_bunrui")
    private Integer shouhizeiBunrui; // 消費税分類

    @Column(name = "shouhizei_bunrui_mei")
    private String shouhizeiBunruiMei; // 消費税分類名

    /**
     * SmilePurchaseFileをWSmilePurchaseOutputFileに変換するメソッド
     *
     * @param smilePurchaseFile Smileから取り込む仕入ファイル
     * @return WSmilePurchaseOutputFileエンティティ
     */
    public static WSmilePurchaseOutputFile convertSmilePurchaseFile(ExtPurchaseFile smilePurchaseFile) {
        return WSmilePurchaseOutputFile.builder()
                .shoriRenban(smilePurchaseFile.get処理連番())
                .gyou(smilePurchaseFile.get行())
                .shopNo(smilePurchaseFile.getShopNo())
                .companyNo(smilePurchaseFile.getCompanyNo())
                .denpyouHizuke(smilePurchaseFile.get伝票日付())
                .nengetsudo(smilePurchaseFile.get年月度())
                .denpyouBangou(smilePurchaseFile.get伝票番号())
                .meisaikubun(smilePurchaseFile.get明細区分())
                .meisaikubunMei(smilePurchaseFile.get明細区分名())
                .shiiresakiCode(smilePurchaseFile.get仕入先コード())
                .shiiresakiMei1(smilePurchaseFile.get仕入先名１())
                .shiiresakiMei2(smilePurchaseFile.get仕入先名２())
                .shiiresakiRyakushou(smilePurchaseFile.get仕入先名略称())
                .shiiresakiEigyoshoCode(smilePurchaseFile.get仕入先営業所コード())
                .shiiresakiEigyoshoMei(smilePurchaseFile.get仕入先営業所名())
                .shiiresakiBumonCode(smilePurchaseFile.get仕入先部門コード())
                .shiiresakiBumonMei(smilePurchaseFile.get仕入先部門名())
                .shiiresakiChikuCode(smilePurchaseFile.get仕入先地区コード())
                .shiiresakiChikuMei(smilePurchaseFile.get仕入先地区名())
                .shiiresakiGyoushuCode(smilePurchaseFile.get仕入先業種コード())
                .shiiresakiGyoushuMei(smilePurchaseFile.get仕入先業種名())
                .shiiresakiBunrui4Code(smilePurchaseFile.get仕入先分類４コード())
                .shiiresakiBunrui4Mei(smilePurchaseFile.get仕入先分類４名())
                .shiiresakiBunrui5Code(smilePurchaseFile.get仕入先分類５コード())
                .shiiresakiBunrui5Mei(smilePurchaseFile.get仕入先分類５名())
                .shiiresakiBunrui6Code(smilePurchaseFile.get仕入先分類６コード())
                .shiiresakiBunrui6Mei(smilePurchaseFile.get仕入先分類６名())
                .shiiresakiBunrui7Code(smilePurchaseFile.get仕入先分類７コード())
                .shiiresakiBunrui7Mei(smilePurchaseFile.get仕入先分類７名())
                .shiiresakiBunrui8Code(smilePurchaseFile.get仕入先分類８コード())
                .shiiresakiBunrui8Mei(smilePurchaseFile.get仕入先分類８名())
                .shiiresakiBunrui9Code(smilePurchaseFile.get仕入先分類９コード())
                .shiiresakiBunrui9Mei(smilePurchaseFile.get仕入先分類９名())
                .shiharaisakiCode(smilePurchaseFile.get支払先コード())
                .shiharaisakiMei(smilePurchaseFile.get支払先名())
                .shiharaisakiEigyoshoCode(smilePurchaseFile.get支払先営業所コード())
                .shiharaisakiEigyoshoMei(smilePurchaseFile.get支払先営業所名())
                .shiharaisakiBumonCode(smilePurchaseFile.get支払先部門コード())
                .shiharaisakiBumonMei(smilePurchaseFile.get支払先部門名())
                .shiharaisakiChikuCode(smilePurchaseFile.get支払先地区コード())
                .shiharaisakiChikuMei(smilePurchaseFile.get支払先地区名())
                .shiharaisakiGyoushuCode(smilePurchaseFile.get支払先業種コード())
                .shiharaisakiGyoushuMei(smilePurchaseFile.get支払先業種名())
                .tantoushaCode(smilePurchaseFile.get担当者コード())
                .tantoushaMei(smilePurchaseFile.get担当者名())
                .tantoushaBunrui0Code(smilePurchaseFile.get担当者分類０コード())
                .tantoushaBunrui0Mei(smilePurchaseFile.get担当者分類０名())
                .tantoushaBunrui1Code(smilePurchaseFile.get担当者分類１コード())
                .tantoushaBunrui1Mei(smilePurchaseFile.get担当者分類１名())
                .tantoushaBunrui2Code(smilePurchaseFile.get担当者分類２コード())
                .tantoushaBunrui2Mei(smilePurchaseFile.get担当者分類２名())
                .tantoushaBunrui3Code(smilePurchaseFile.get担当者分類３コード())
                .tantoushaBunrui3Mei(smilePurchaseFile.get担当者分類３名())
                .tantoushaBunrui4Code(smilePurchaseFile.get担当者分類４コード())
                .tantoushaBunrui4Mei(smilePurchaseFile.get担当者分類４名())
                .tantoushaBunrui5Code(smilePurchaseFile.get担当者分類５コード())
                .tantoushaBunrui5Mei(smilePurchaseFile.get担当者分類５名())
                .tantoushaBunrui6Code(smilePurchaseFile.get担当者分類６コード())
                .tantoushaBunrui6Mei(smilePurchaseFile.get担当者分類６名())
                .tantoushaBunrui7Code(smilePurchaseFile.get担当者分類７コード())
                .tantoushaBunrui7Mei(smilePurchaseFile.get担当者分類７名())
                .tantoushaBunrui8Code(smilePurchaseFile.get担当者分類８コード())
                .tantoushaBunrui8Mei(smilePurchaseFile.get担当者分類８名())
                .tantoushaBunrui9Code(smilePurchaseFile.get担当者分類９コード())
                .tantoushaBunrui9Mei(smilePurchaseFile.get担当者分類９名())
                .shiharai(smilePurchaseFile.get支払())
                .shiharaiKubunMei(smilePurchaseFile.get支払区分名())
                .kaikakeKubun(smilePurchaseFile.get買掛区分())
                .kaikakeKubunMei(smilePurchaseFile.get買掛区分名())
                .torihikiKubun(smilePurchaseFile.get取引区分())
                .torihikiKubunMei(smilePurchaseFile.get取引区分名())
                .torihikiKubunZokusei(smilePurchaseFile.get取引区分属性())
                .torihikiKubunZokuseiMei(smilePurchaseFile.get取引区分属性名())
                .shouhinCode(smilePurchaseFile.get商品コード())
                .shouhinMei(smilePurchaseFile.get商品名())
                .makerCode(smilePurchaseFile.getメーカーコード())
                .makerMei(smilePurchaseFile.getメーカー名())
                .shouhinBunruiCode(smilePurchaseFile.get商品分類コード())
                .shouhinBunruiMei(smilePurchaseFile.get商品分類名())
                .irisu(smilePurchaseFile.get入数())
                .kosuu(smilePurchaseFile.get個数())
                .kosuuTanni(smilePurchaseFile.get個数単位())
                .suuryou(smilePurchaseFile.get数量())
                .suuryouTanni(smilePurchaseFile.get数量単位())
                .tanka(smilePurchaseFile.get単価())
                .kingaku(smilePurchaseFile.get金額())
                .tankaKakuritsu(smilePurchaseFile.get単価掛率())
                .kazeiKubun(smilePurchaseFile.get課税区分())
                .kazeiKubunMei(smilePurchaseFile.get課税区分名())
                .shouhizeiritsu(smilePurchaseFile.get消費税率())
                .uchishouhizei(smilePurchaseFile.get内消費税等())
                .gyouTekiyouCode(smilePurchaseFile.get行摘要コード())
                .gyouTekiyou1(smilePurchaseFile.get行摘要１())
                .gyouTekiyou2(smilePurchaseFile.get行摘要２())
                .bikoCode(smilePurchaseFile.get備考コード())
                .biko(smilePurchaseFile.get備考())
                .loginId(smilePurchaseFile.getログインID())
                .loginMei(smilePurchaseFile.getログイン名())
                .sousaHizuke(smilePurchaseFile.get操作日付())
                .hacchuuBangou(smilePurchaseFile.get発注番号())
                .hacchuuGyou(smilePurchaseFile.get発注行())
                .orderNo(smilePurchaseFile.getオーダーNo())
                .jidouSeiseiKubun(smilePurchaseFile.get自動生成区分())
                .jidouSeiseiKubunMei(smilePurchaseFile.get自動生成区分名())
                .denpyouShouhizeiKeisanKubun(smilePurchaseFile.get伝票消費税計算区分())
                .denpyouShouhizeiKeisanKubunMei(smilePurchaseFile.get伝票消費税計算区分名())
                .dataHasseiKubun(smilePurchaseFile.getデータ発生区分())
                .aiteShoriRenban(smilePurchaseFile.get相手処理連番())
                .nyuuryokuPatternNo(smilePurchaseFile.get入力パターンNo())
                .nyuuryokuPatternMei(smilePurchaseFile.get入力パターン名())
                .checkmarkKubun(smilePurchaseFile.getチェックマーク区分())
                .checkmarkKubunMei(smilePurchaseFile.getチェックマーク区分名())
                .shouhizeiBunrui(smilePurchaseFile.get消費税分類())
                .shouhizeiBunruiMei(smilePurchaseFile.get消費税分類名())
                .build();
    }


}
