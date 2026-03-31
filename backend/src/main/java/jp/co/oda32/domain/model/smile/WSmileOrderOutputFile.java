package jp.co.oda32.domain.model.smile;

import jp.co.oda32.batch.smile.SmileOrderFile;
import jp.co.oda32.domain.model.embeddable.WSmileOrderOutputFilePK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Smileから出力される売上明細ファイルに対応したテーブルのEntity
 *
 * @author k_oda
 * @since 2024/05/08
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "w_smile_order_output_file")
@IdClass(WSmileOrderOutputFilePK.class)
public class WSmileOrderOutputFile implements ISmileGoodsFile {

    @Id
    @Column(name = "shori_renban")
    private Long shoriRenban; // 処理連番

    @Id
    @Column(name = "gyou")
    private Integer gyou; // 行

    @Id
    @Column(name = "shop_no")
    private Integer shopNo;

    @Column(name = "meisaikubun")
    private String meisaikubun; // 明細区分

    @Column(name = "meisaikubun_mei")
    private String meisaikubunMei; // 明細区分名

    @Column(name = "denpyou_hizuke")
    private LocalDate denpyouHizuke; // 伝票日付

    @Column(name = "nengetsudo")
    private String nengetsudo; // 年月度

    @Column(name = "denpyou_bangou")
    private String denpyouBangou; // 伝票番号

    @Column(name = "tokuisaki_code")
    private String tokuisakiCode; // 得意先コード

    @Column(name = "tokuisaki_mei1")
    private String tokuisakiMei1; // 得意先名1

    @Column(name = "tokuisaki_mei2")
    private String tokuisakiMei2; // 得意先名2

    @Column(name = "tokuisaki_ryakushou")
    private String tokuisakiRyakushou; // 得意先名略称

    @Column(name = "tokuisaki_eigyosho_code")
    private String tokuisakiEigyoshoCode; // 得意先営業所コード

    @Column(name = "tokuisaki_eigyosho_mei")
    private String tokuisakiEigyoshoMei; // 得意先営業所名

    @Column(name = "tokuisaki_bumon_code")
    private String tokuisakiBumonCode; // 得意先部門コード

    @Column(name = "tokuisaki_bumon_mei")
    private String tokuisakiBumonMei; // 得意先部門名

    @Column(name = "tokuisaki_chiku_code")
    private String tokuisakiChikuCode; // 得意先地区コード

    @Column(name = "tokuisaki_chiku_mei")
    private String tokuisakiChikuMei; // 得意先地区名

    @Column(name = "tokuisaki_gyoushu_code")
    private String tokuisakiGyoushuCode; // 得意先業種コード

    @Column(name = "tokuisaki_gyoushu_mei")
    private String tokuisakiGyoushuMei; // 得意先業種名

    @Column(name = "tokuisaki_group_code")
    private String tokuisakiGroupCode; // 得意先グループコード

    @Column(name = "tokuisaki_group_mei")
    private String tokuisakiGroupMei; // 得意先グループ名

    @Column(name = "tokuisaki_tanka_rank_code")
    private String tokuisakiTankaRankCode; // 得意先単価ランクコード

    @Column(name = "tokuisaki_tanka_rank_mei")
    private String tokuisakiTankaRankMei; // 得意先単価ランク名
    @Column(name = "seikyusaki_code")
    private String seikyusakiCode; // 請求先コード

    @Column(name = "seikyusaki_mei")
    private String seikyusakiMei; // 請求先名

    @Column(name = "seikyusaki_eigyosho_code")
    private String seikyusakiEigyoshoCode; // 請求先営業所コード

    @Column(name = "seikyusaki_eigyosho_mei")
    private String seikyusakiEigyoshoMei; // 請求先営業所名

    @Column(name = "seikyusaki_bumon_code")
    private String seikyusakiBumonCode; // 請求先部門コード

    @Column(name = "seikyusaki_bumon_mei")
    private String seikyusakiBumonMei; // 請求先部門名

    @Column(name = "seikyusaki_chiku_code")
    private String seikyusakiChikuCode; // 請求先地区コード

    @Column(name = "seikyusaki_chiku_mei")
    private String seikyusakiChikuMei; // 請求先地区名

    @Column(name = "seikyusaki_gyoushu_code")
    private String seikyusakiGyoushuCode; // 請求先業種コード

    @Column(name = "seikyusaki_gyoushu_mei")
    private String seikyusakiGyoushuMei; // 請求先業種名

    @Column(name = "nouhin_saki_code")
    private String nouhinSakiCode; // 納品先コード

    @Column(name = "nouhin_saki_mei")
    private String nouhinSakiMei; // 納品先名

    @Column(name = "tantousha_code")
    private String tantoushaCode; // 担当者コード

    @Column(name = "tantousha_mei")
    private String tantoushaMei; // 担当者名

    // 担当者の分類情報
    @Column(name = "tantousha_bunrui0_code")
    private String tantoushaBunrui0Code; // 担当者分類0コード

    @Column(name = "tantousha_bunrui0_mei")
    private String tantoushaBunrui0Mei; // 担当者分類0名

    @Column(name = "tantousha_bunrui1_code")
    private String tantoushaBunrui1Code; // 担当者分類1コード

    @Column(name = "tantousha_bunrui1_mei")
    private String tantoushaBunrui1Mei; // 担当者分類1名

    @Column(name = "urikake_kubun")
    private String urikakeKubun; // 売掛区分

    // 商品情報
    @Column(name = "shouhin_code")
    private String shouhinCode; // 商品コード

    @Column(name = "shouhin_mei")
    private String shouhinMei; // 商品名

    @Column(name = "maker_code")
    private String makerCode; // メーカーコード

    @Column(name = "maker_mei")
    private String makerMei; // メーカー名

    // 取引情報
    @Column(name = "torihiki_kubun")
    private BigDecimal torihikiKubun; // 取引区分

    @Column(name = "torihiki_kubun_mei")
    private String torihikiKubunMei; // 取引区分名

    // 商品分類情報
    @Column(name = "shouhin_bunrui_code")
    private String shouhinBunruiCode; // 商品分類コード

    @Column(name = "shouhin_bunrui_mei")
    private String shouhinBunruiMei; // 商品分類名

    @Column(name = "shouhin_bunrui2_code")
    private String shouhinBunrui2Code; // 商品分類2コード

    @Column(name = "shouhin_bunrui2_mei")
    private String shouhinBunrui2Mei; // 商品分類2名

    @Column(name = "shouhin_bunrui3_code")
    private String shouhinBunrui3Code; // 商品分類3コード

    @Column(name = "shouhin_bunrui3_mei")
    private String shouhinBunrui3Mei; // 商品分類3名

    @Column(name = "shouhin_bunrui4_code")
    private String shouhinBunrui4Code; // 商品分類4コード

    @Column(name = "shouhin_bunrui4_mei")
    private String shouhinBunrui4Mei; // 商品分類4名

    @Column(name = "shouhin_bunrui5_code")
    private String shouhinBunrui5Code; // 商品分類5コード

    @Column(name = "shouhin_bunrui5_mei")
    private String shouhinBunrui5Mei; // 商品分類5名

    @Column(name = "shouhin_bunrui6_code")
    private String shouhinBunrui6Code; // 商品分類6コード

    @Column(name = "shouhin_bunrui6_mei")
    private String shouhinBunrui6Mei; // 商品分類6名

    @Column(name = "shouhin_bunrui7_code")
    private String shouhinBunrui7Code; // 商品分類7コード

    @Column(name = "shouhin_bunrui7_mei")
    private String shouhinBunrui7Mei; // 商品分類7名

    @Column(name = "shouhin_bunrui8_code")
    private String shouhinBunrui8Code; // 商品分類8コード

    @Column(name = "shouhin_bunrui8_mei")
    private String shouhinBunrui8Mei; // 商品分類8名

    @Column(name = "shouhin_bunrui9_code")
    private String shouhinBunrui9Code; // 商品分類9コード

    @Column(name = "shouhin_bunrui9_mei")
    private String shouhinBunrui9Mei; // 商品分類9名

    // その他の情報
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

    @Column(name = "gen_tanka")
    private BigDecimal genTanka; // 原単価

    @Column(name = "genka_kingaku")
    private BigDecimal genkaKingaku; // 原価金額

    @Column(name = "arari")
    private BigDecimal arari; // 粗利

    @Column(name = "tanka_kakuritsu")
    private BigDecimal tankaKakuritsu; // 単価掛率

    @Column(name = "kazei_kubun")
    private String kazeiKubun; // 課税区分

    @Column(name = "kazei_kubun_mei")
    private String kazeiKubunMei; // 課税区分名

    // 税金情報
    @Column(name = "shouhizeiritsu")
    private BigDecimal shouhizeiritsu; // 消費税率

    @Column(name = "nai_shouhizei_etc")
    private BigDecimal naiShouhizeiEtc; // 内消費税等
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

    @Column(name = "juchuu_bangou")
    private BigDecimal juchuuBangou; // 受注番号

    @Column(name = "juchuu_gyou")
    private BigDecimal juchuuGyou; // 受注行

    @Column(name = "order_bangou")
    private String orderBangou; // オーダー番号

    @Column(name = "mitumori_shori_renban")
    private BigDecimal mitumoriShoriRenban; // 見積処理連番

    @Column(name = "mitumori_gyou")
    private BigDecimal mitumoriGyou; // 見積行

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

    @Column(name = "nyuuryoku_pattern_bangou")
    private String nyuuryokuPatternBangou; // 入力パターン番号

    @Column(name = "nyuuryoku_pattern_mei")
    private String nyuuryokuPatternMei; // 入力パターン名

    @Column(name = "fushiyou_denpyou_bangou")
    private String fushiyouDenpyouBangou; // 不使用伝票番号

    @Column(name = "aite_denpyou_bangou")
    private String aiteDenpyouBangou; // 相手伝票番号

    @Column(name = "code")
    private String code; // コード

    @Column(name = "fushiyou_kazei_kubun")
    private String fushiyouKazeiKubun; // 不使用課税区分

    @Column(name = "fushiyou_code")
    private String fushiyouCode; // 不使用コード

    @Column(name = "chokusou_kubun")
    private String chokusouKubun; // 直送区分

    @Column(name = "shoten_code")
    private String shotenCode; // 社店コード

    @Column(name = "bunrui_code")
    private String bunruiCode; // 分類コード

    @Column(name = "denpyou_kubun")
    private String denpyouKubun; // 伝票区分

    @Column(name = "torihiki_code")
    private String torihikiCode; // 取引先コード

    @Column(name = "uri_tanka")
    private BigDecimal uriTanka; // 売単価

    @Column(name = "aite_shouhin_code")
    private String aiteShouhinCode; // 相手商品コード

    @Column(name = "chekkumaku_kubun")
    private BigDecimal chekkumakuKubun; // チェックマーク区分

    @Column(name = "chekkumaku_kubun_mei")
    private String chekkumakuKubunMei; // チェックマーク区分名

    @Column(name = "shouhizei_bunrui")
    private Integer shouhizeiBunrui; // 消費税分類

    @Column(name = "shouhizei_bunrui_mei")
    private String shouhizeiBunruiMei; // 消費税分類名

    public WSmileOrderOutputFile convertSmileOrderFile(SmileOrderFile smileOrderFile) {
        return WSmileOrderOutputFile.builder()
                .shoriRenban(smileOrderFile.get処理連番())
                .gyou(smileOrderFile.get行())
                .meisaikubun(smileOrderFile.get明細区分())
                .meisaikubunMei(smileOrderFile.get明細区分名())
                .denpyouHizuke(smileOrderFile.get伝票日付())
                .nengetsudo(smileOrderFile.get年月度())
                .denpyouBangou(smileOrderFile.get伝票番号())
                .tokuisakiCode(smileOrderFile.get得意先コード())
                .tokuisakiMei1(smileOrderFile.get得意先名1())
                .tokuisakiMei2(smileOrderFile.get得意先名2())
                .tokuisakiRyakushou(smileOrderFile.get得意先名略称())
                .tokuisakiEigyoshoCode(smileOrderFile.get得意先営業所コード())
                .tokuisakiEigyoshoMei(smileOrderFile.get得意先営業所名())
                .tokuisakiBumonCode(smileOrderFile.get得意先部門コード())
                .tokuisakiBumonMei(smileOrderFile.get得意先部門名())
                .tokuisakiChikuCode(smileOrderFile.get得意先地区コード())
                .tokuisakiChikuMei(smileOrderFile.get得意先地区名())
                .tokuisakiGyoushuCode(smileOrderFile.get得意先業種コード())
                .tokuisakiGyoushuMei(smileOrderFile.get得意先業種名())
                .tokuisakiGroupCode(smileOrderFile.get得意先グループコード())
                .tokuisakiGroupMei(smileOrderFile.get得意先グループ名())
                .tokuisakiTankaRankCode(smileOrderFile.get得意先単価ランクコード())
                .tokuisakiTankaRankMei(smileOrderFile.get得意先単価ランク名())
                .seikyusakiCode(smileOrderFile.get請求先コード())
                .seikyusakiMei(smileOrderFile.get請求先名())
                .seikyusakiEigyoshoCode(smileOrderFile.get請求先営業所コード())
                .seikyusakiEigyoshoMei(smileOrderFile.get請求先営業所名())
                .seikyusakiBumonCode(smileOrderFile.get請求先部門コード())
                .seikyusakiBumonMei(smileOrderFile.get請求先部門名())
                .seikyusakiChikuCode(smileOrderFile.get請求先地区コード())
                .seikyusakiChikuMei(smileOrderFile.get請求先地区名())
                .seikyusakiGyoushuCode(smileOrderFile.get請求先業種コード())
                .seikyusakiGyoushuMei(smileOrderFile.get請求先業種名())
                .nouhinSakiCode(smileOrderFile.get納品先コード())
                .nouhinSakiMei(smileOrderFile.get納品先名())
                .tantoushaCode(smileOrderFile.get担当者コード())
                .tantoushaMei(smileOrderFile.get担当者名())
                .tantoushaBunrui0Code(smileOrderFile.get担当者分類0コード())
                .tantoushaBunrui0Mei(smileOrderFile.get担当者分類0名())
                .tantoushaBunrui1Code(smileOrderFile.get担当者分類1コード())
                .tantoushaBunrui1Mei(smileOrderFile.get担当者分類1名())
                .shouhinCode(smileOrderFile.get商品コード())
                .shouhinMei(smileOrderFile.get商品名())
                .makerCode(smileOrderFile.getメーカーコード())
                .makerMei(smileOrderFile.getメーカー名())
                .torihikiKubun(smileOrderFile.get取引区分())
                .torihikiKubunMei(smileOrderFile.get取引区分名())
                .shouhinBunruiCode(smileOrderFile.get商品分類コード())
                .shouhinBunruiMei(smileOrderFile.get商品分類名())
                .shouhinBunrui2Code(smileOrderFile.get商品分類2コード())
                .shouhinBunrui2Mei(smileOrderFile.get商品分類2名())
                .shouhinBunrui3Code(smileOrderFile.get商品分類3コード())
                .shouhinBunrui3Mei(smileOrderFile.get商品分類3名())
                .shouhinBunrui4Code(smileOrderFile.get商品分類4コード())
                .shouhinBunrui4Mei(smileOrderFile.get商品分類4名())
                .shouhinBunrui5Code(smileOrderFile.get商品分類5コード())
                .shouhinBunrui5Mei(smileOrderFile.get商品分類5名())
                .shouhinBunrui6Code(smileOrderFile.get商品分類6コード())
                .shouhinBunrui6Mei(smileOrderFile.get商品分類6名())
                .shouhinBunrui7Code(smileOrderFile.get商品分類7コード())
                .shouhinBunrui7Mei(smileOrderFile.get商品分類7名())
                .shouhinBunrui8Code(smileOrderFile.get商品分類8コード())
                .shouhinBunrui8Mei(smileOrderFile.get商品分類8名())
                .shouhinBunrui9Code(smileOrderFile.get商品分類9コード())
                .shouhinBunrui9Mei(smileOrderFile.get商品分類9名())
                .irisu(smileOrderFile.get入数())
                .kosuu(smileOrderFile.get個数())
                .kosuuTanni(smileOrderFile.get個数単位())
                .suuryou(smileOrderFile.get数量())
                .suuryouTanni(smileOrderFile.get数量単位())
                .tanka(smileOrderFile.get単価())
                .kingaku(smileOrderFile.get金額())
                .genTanka(smileOrderFile.get原単価())
                .genkaKingaku(smileOrderFile.get原価金額())
                .arari(smileOrderFile.get粗利())
                .tankaKakuritsu(smileOrderFile.get単価掛率())
                .kazeiKubun(smileOrderFile.get課税区分())
                .kazeiKubunMei(smileOrderFile.get課税区分名())
                .shouhizeiritsu(smileOrderFile.get消費税率())
                .naiShouhizeiEtc(smileOrderFile.get内消費税等())
                .gyouTekiyouCode(smileOrderFile.get行摘要コード())
                .gyouTekiyou1(smileOrderFile.get行摘要1())
                .gyouTekiyou2(smileOrderFile.get行摘要2())
                .bikoCode(smileOrderFile.get備考コード())
                .biko(smileOrderFile.get備考())
                .loginId(smileOrderFile.getログインＩＤ())
                .loginMei(smileOrderFile.getログイン名())
                .sousaHizuke(smileOrderFile.get操作日付())
                .juchuuBangou(smileOrderFile.get受注番号())
                .juchuuGyou(smileOrderFile.get受注行())
                .orderBangou(smileOrderFile.getオーダー番号())
                .mitumoriShoriRenban(smileOrderFile.get見積処理連番())
                .mitumoriGyou(smileOrderFile.get見積行())
                .jidouSeiseiKubun(smileOrderFile.get自動生成区分())
                .jidouSeiseiKubunMei(smileOrderFile.get自動生成区分名())
                .denpyouShouhizeiKeisanKubun(smileOrderFile.get伝票消費税計算区分())
                .denpyouShouhizeiKeisanKubunMei(smileOrderFile.get伝票消費税計算区分名())
                .dataHasseiKubun(smileOrderFile.getデータ発生区分())
                .aiteShoriRenban(smileOrderFile.get相手処理連番())
                .nyuuryokuPatternBangou(smileOrderFile.get入力パターン番号())
                .nyuuryokuPatternMei(smileOrderFile.get入力パターン名())
                .fushiyouDenpyouBangou(smileOrderFile.get不使用伝票番号())
                .aiteDenpyouBangou(smileOrderFile.get相手伝票番号())
                .code(smileOrderFile.getコード())
                .fushiyouKazeiKubun(smileOrderFile.get不使用課税区分())
                .fushiyouCode(smileOrderFile.get不使用コード())
                .chokusouKubun(smileOrderFile.get直送区分())
                .shotenCode(smileOrderFile.get社店コード())
                .bunruiCode(smileOrderFile.get分類コード())
                .denpyouKubun(smileOrderFile.get伝票区分())
                .torihikiCode(smileOrderFile.get取引先コード())
                .uriTanka(smileOrderFile.get売単価())
                .aiteShouhinCode(smileOrderFile.get相手商品コード())
                .chekkumakuKubun(smileOrderFile.getチェックマーク区分())
                .chekkumakuKubunMei(smileOrderFile.getチェックマーク区分名())
                .shouhizeiBunrui(smileOrderFile.get消費税分類())
                .shouhizeiBunruiMei(smileOrderFile.get消費税分類名())
                .shopNo(smileOrderFile.getShopNo())
                .build();
    }
}
