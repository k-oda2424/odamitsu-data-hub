package jp.co.oda32.batch.smile;

import jp.co.oda32.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 取り込み用得意先ファイルフォーマット
 *
 * @author k_oda
 * @since 2024/06/11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PartnerFile {
    private String 得意先コード;
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

    public static PartnerFile parseFromCSV(String csvLine) {
        String[] values = csvLine.split(",", -1);
        Map<String, String> errorMap = new HashMap<>();
        PartnerFile partnerFile = new PartnerFile();
        for (int i = 0; i < values.length; i++) {
            System.out.println("values[" + i + "]:" + values[i]);
        }
        try {
            partnerFile.set得意先コード(values[0]);
            partnerFile.set得意先名1(values[1]);
            partnerFile.set得意先名2(values[2]);
            partnerFile.set得意先名略称(values[3]);
            partnerFile.set得意先名索引(values[4]);
            partnerFile.set郵便番号(values[5]);
            partnerFile.set住所1(values[6]);
            partnerFile.set住所2(values[7]);
            partnerFile.set住所3(values[8]);
            partnerFile.setカスタマバーコード(values[9]);
            partnerFile.set電話番号(values[10]);
            partnerFile.setFAX番号(values[11]);
            partnerFile.set営業所コード(values[12]);
            partnerFile.set営業所名(values[13]);
            partnerFile.set部門コード(values[14]);
            partnerFile.set部門名(values[15]);
            partnerFile.set地区コード(values[16]);
            partnerFile.set地区名(values[17]);
            partnerFile.set業種コード(values[18]);
            partnerFile.set業種名(values[19]);
            partnerFile.setグループコード(values[20]);
            partnerFile.setグループ名(values[21]);
            partnerFile.set単価ランクコード(values[22]);
            partnerFile.set単価ランク名(values[23]);
            partnerFile.set得意先分類6コード(values[24]);
            partnerFile.set得意先分類6名(values[25]);
            partnerFile.set得意先分類7コード(values[26]);
            partnerFile.set得意先分類7名(values[27]);
            partnerFile.set得意先分類8コード(values[28]);
            partnerFile.set得意先分類8名(values[29]);
            partnerFile.set得意先分類9コード(values[30]);
            partnerFile.set得意先分類9名(values[31]);
            partnerFile.set請求先コード(values[32]);
            partnerFile.set請求先名(values[33]);
            partnerFile.set請求先区分(Integer.parseInt(values[34]));
            partnerFile.set請求先区分名(values[35]);
            partnerFile.set請求先営業所コード(values[36]);
            partnerFile.set請求先営業所名(values[37]);
            partnerFile.set請求先部門コード(values[38]);
            partnerFile.set請求先部門名(values[39]);
            partnerFile.set請求先地区コード(values[40]);
            partnerFile.set請求先地区名(values[41]);
            partnerFile.set請求先業種コード(values[42]);
            partnerFile.set請求先業種名(values[43]);
            partnerFile.set請求先グループコード(values[44]);
            partnerFile.set請求先グループ名(values[45]);
            partnerFile.set請求先単価ランクコード(values[46]);
            partnerFile.set請求先単価ランク名(values[47]);
            partnerFile.set請求先分類6コード(values[48]);
            partnerFile.set請求先分類6名(values[49]);
            partnerFile.set請求先分類7コード(values[50]);
            partnerFile.set請求先分類7名(values[51]);
            partnerFile.set請求先分類8コード(values[52]);
            partnerFile.set請求先分類8名(values[53]);
            partnerFile.set請求先分類9コード(values[54]);
            partnerFile.set請求先分類9名(values[55]);
            partnerFile.set担当者コード(values[56]);
            partnerFile.set担当者名(values[57]);
            partnerFile.set担当者分類0コード(values[58]);
            partnerFile.set担当者分類0名(values[59]);
            partnerFile.set担当者分類1コード(values[60]);
            partnerFile.set担当者分類1名(values[61]);
            partnerFile.set担当者分類2コード(values[62]);
            partnerFile.set担当者分類2名(values[63]);
            partnerFile.set担当者分類3コード(values[64]);
            partnerFile.set担当者分類3名(values[65]);
            partnerFile.set担当者分類4コード(values[66]);
            partnerFile.set担当者分類4名(values[67]);
            partnerFile.set担当者分類5コード(values[68]);
            partnerFile.set担当者分類5名(values[69]);
            partnerFile.set担当者分類6コード(values[70]);
            partnerFile.set担当者分類6名(values[71]);
            partnerFile.set担当者分類7コード(values[72]);
            partnerFile.set担当者分類7名(values[73]);
            partnerFile.set担当者分類8コード(values[74]);
            partnerFile.set担当者分類8名(values[75]);
            partnerFile.set担当者分類9コード(values[76]);
            partnerFile.set担当者分類9名(values[77]);
            partnerFile.set締日1(Integer.parseInt(values[78]));
            partnerFile.set締日2(Integer.parseInt(values[79]));
            partnerFile.set締日3(Integer.parseInt(values[80]));
            partnerFile.set入金日1(Integer.parseInt(values[81]));
            partnerFile.set入金日2(Integer.parseInt(values[82]));
            partnerFile.set入金日3(Integer.parseInt(values[83]));
            partnerFile.set入金サイクル1(Integer.parseInt(values[84]));
            partnerFile.set入金サイクル名1(values[85]);
            partnerFile.set入金サイクル2(Integer.parseInt(values[86]));
            partnerFile.set入金サイクル名2(values[87]);
            partnerFile.set入金サイクル3(Integer.parseInt(values[88]));
            partnerFile.set入金サイクル名3(values[89]);
            partnerFile.set入金条件1(Integer.parseInt(values[90]));
            partnerFile.set入金条件名1(values[91]);
            partnerFile.set入金条件2(Integer.parseInt(values[92]));
            partnerFile.set入金条件名2(values[93]);
            partnerFile.set入金条件3(Integer.parseInt(values[94]));
            partnerFile.set入金条件名3(values[95]);
            partnerFile.set与信限度額(new BigDecimal(values[96]));
            partnerFile.set単価掛率区分(Integer.parseInt(values[97]));
            partnerFile.set単価掛率区分名(values[98]);
            partnerFile.set単価掛率(new BigDecimal(values[99]));
            partnerFile.set単価ランク(Integer.parseInt(values[100]));
            partnerFile.set単価処理区分(Integer.parseInt(values[101]));
            partnerFile.set単価処理区分名(values[102]);
            partnerFile.set単価処理単位(new BigDecimal(values[103]));
            partnerFile.set金額処理区分(Integer.parseInt(values[104]));
            partnerFile.set金額処理区分名(values[105]);
            partnerFile.set課税対象区分(Integer.parseInt(values[106]));
            partnerFile.set課税対象区分名(values[107]);
            partnerFile.set売上単価設定区分(Integer.parseInt(values[108]));
            partnerFile.set売上単価設定区分名(values[109]);
            partnerFile.set上代単価設定区分(Integer.parseInt(values[110]));
            partnerFile.set上代単価設定区分名(values[111]);
            partnerFile.set単価変換区分(Integer.parseInt(values[112]));
            partnerFile.set単価変換区分名(values[113]);
            partnerFile.set消費税通知区分(Integer.parseInt(values[114]));
            partnerFile.set消費税通知区分名(values[115]);
            partnerFile.set消費税計算区分(Integer.parseInt(values[116]));
            partnerFile.set消費税計算区分名(values[117]);
            partnerFile.set消費税計算単位(new BigDecimal(values[118]));
            partnerFile.set消費税分解区分(Integer.parseInt(values[119]));
            partnerFile.set消費税分解区分名(values[120]);
            partnerFile.set請求書出力タイプ(Integer.parseInt(values[121]));
            partnerFile.set請求書出力タイプ名(values[122]);
            partnerFile.set請求書出力形式(Integer.parseInt(values[123]));
            partnerFile.set請求書出力形式名(values[124]);
            partnerFile.set得意先台帳出力形式(Integer.parseInt(values[125]));
            partnerFile.set得意先台帳出力形式名(values[126]);
            partnerFile.set請求消費税算出単位(Integer.parseInt(values[127]));
            partnerFile.set請求消費税算出単位名(values[128]);
            partnerFile.set期首売掛残高(new BigDecimal(values[129]));
            partnerFile.set前回請求残高(new BigDecimal(values[130]));
            partnerFile.set相殺仕入先コード(values[131]);
            partnerFile.set相殺仕入先名(values[132]);
            partnerFile.set日付印字区分(Integer.parseInt(values[133]));
            partnerFile.set日付印字区分名(values[134]);
            partnerFile.set相手先担当者名(values[135]);
            partnerFile.set取引(values[136]);
            partnerFile.set取引名(values[137]);
            partnerFile.set売上伝票会社名パターン(values[138]);
            partnerFile.set売上伝票会社名名称(values[139]);
            partnerFile.set見積書会社名パターン(values[140]);
            partnerFile.set見積書会社名名称(values[141]);
            partnerFile.set請求書会社名パターン(values[142]);
            partnerFile.set請求書会社名名称(values[143]);
            partnerFile.setマスター検索表示区分(Integer.parseInt(values[144]));
            partnerFile.setマスター検索表示区分名(values[145]);
            partnerFile.set伝票出力区分(Integer.parseInt(values[146]));
            partnerFile.set伝票出力区分名(values[147]);
            partnerFile.set変換コード伝票出力区分(Integer.parseInt(values[148]));
            partnerFile.set変換コード伝票出力区分名(values[149]);
            partnerFile.set変換コード見積書出力区分(Integer.parseInt(values[150]));
            partnerFile.set変換コード見積書出力区分名(values[151]);
            partnerFile.set変換コード請求書出力区分(Integer.parseInt(values[152]));
            partnerFile.set変換コード請求書出力区分名(values[153]);
            partnerFile.set入力処理モード(Integer.parseInt(values[154]));
            partnerFile.set入力処理モード名(values[155]);
            partnerFile.set個別設定入力行数(Integer.parseInt(values[156]));
            partnerFile.set社店コード(values[157]);
            partnerFile.set分類コード(values[158]);
            partnerFile.set伝票区分(StringUtil.isEmpty(values[159]) ? null : Integer.valueOf(values[159]));
            partnerFile.set取引先コード(values[160]);
            partnerFile.set有効期間開始日(values[161]);
            partnerFile.set有効期間終了日(values[162]);
            partnerFile.set操作日付(values[163]);
            partnerFile.setログインID(values[164]);
            partnerFile.setログイン名(values[165]);
            partnerFile.set登録番号売上伝票出力(Integer.parseInt(values[166]));
            partnerFile.set登録番号売上伝票出力名(values[167]);
            partnerFile.set登録番号請求書出力(Integer.parseInt(values[168]));
            partnerFile.set登録番号請求書出力名(values[169]);
            partnerFile.set自社負担手数料入金後売上値引(Integer.parseInt(values[170]));
            partnerFile.set自社負担手数料入金後売上値引名(values[171]);
        } catch (NumberFormatException e) {
            errorMap.put("NumberFormatException", e.getMessage());
        } catch (Exception e) {
            errorMap.put("Exception", e.getMessage());
        }

        if (!errorMap.isEmpty()) {
            errorMap.forEach((k, v) -> {
                System.out.println("Error: " + k + " - " + v);
            });
        }

        return partnerFile;
    }

    public static String[] getPartnerFileFormat() {
        return new String[]{
                "得意先コード",
                "得意先名1",
                "得意先名2",
                "得意先名略称",
                "得意先名索引",
                "郵便番号",
                "住所1",
                "住所2",
                "住所3",
                "カスタマバーコード",
                "電話番号",
                "FAX番号",
                "営業所コード",
                "営業所名",
                "部門コード",
                "部門名",
                "地区コード",
                "地区名",
                "業種コード",
                "業種名",
                "グループコード",
                "グループ名",
                "単価ランクコード",
                "単価ランク名",
                "得意先分類6コード",
                "得意先分類6名",
                "得意先分類7コード",
                "得意先分類7名",
                "得意先分類8コード",
                "得意先分類8名",
                "得意先分類9コード",
                "得意先分類9名",
                "請求先コード",
                "請求先名",
                "請求先区分",
                "請求先区分名",
                "請求先営業所コード",
                "請求先営業所名",
                "請求先部門コード",
                "請求先部門名",
                "請求先地区コード",
                "請求先地区名",
                "請求先業種コード",
                "請求先業種名",
                "請求先グループコード",
                "請求先グループ名",
                "請求先単価ランクコード",
                "請求先単価ランク名",
                "請求先分類6コード",
                "請求先分類6名",
                "請求先分類7コード",
                "請求先分類7名",
                "請求先分類8コード",
                "請求先分類8名",
                "請求先分類9コード",
                "請求先分類9名",
                "担当者コード",
                "担当者名",
                "担当者分類0コード",
                "担当者分類0名",
                "担当者分類1コード",
                "担当者分類1名",
                "担当者分類2コード",
                "担当者分類2名",
                "担当者分類3コード",
                "担当者分類3名",
                "担当者分類4コード",
                "担当者分類4名",
                "担当者分類5コード",
                "担当者分類5名",
                "担当者分類6コード",
                "担当者分類6名",
                "担当者分類7コード",
                "担当者分類7名",
                "担当者分類8コード",
                "担当者分類8名",
                "担当者分類9コード",
                "担当者分類9名",
                "締日1",
                "締日2",
                "締日3",
                "入金日1",
                "入金日2",
                "入金日3",
                "入金サイクル1",
                "入金サイクル名1",
                "入金サイクル2",
                "入金サイクル名2",
                "入金サイクル3",
                "入金サイクル名3",
                "入金条件1",
                "入金条件名1",
                "入金条件2",
                "入金条件名2",
                "入金条件3",
                "入金条件名3",
                "与信限度額",
                "単価掛率区分",
                "単価掛率区分名",
                "単価掛率",
                "単価ランク",
                "単価処理区分",
                "単価処理区分名",
                "単価処理単位",
                "金額処理区分",
                "金額処理区分名",
                "課税対象区分",
                "課税対象区分名",
                "売上単価設定区分",
                "売上単価設定区分名",
                "上代単価設定区分",
                "上代単価設定区分名",
                "単価変換区分",
                "単価変換区分名",
                "消費税通知区分",
                "消費税通知区分名",
                "消費税計算区分",
                "消費税計算区分名",
                "消費税計算単位",
                "消費税分解区分",
                "消費税分解区分名",
                "請求書出力タイプ",
                "請求書出力タイプ名",
                "請求書出力形式",
                "請求書出力形式名",
                "得意先台帳出力形式",
                "得意先台帳出力形式名",
                "請求消費税算出単位",
                "請求消費税算出単位名",
                "期首売掛残高",
                "前回請求残高",
                "相殺仕入先コード",
                "相殺仕入先名",
                "日付印字区分",
                "日付印字区分名",
                "相手先担当者名",
                "取引",
                "取引名",
                "売上伝票会社名パターン",
                "売上伝票会社名名称",
                "見積書会社名パターン",
                "見積書会社名名称",
                "請求書会社名パターン",
                "請求書会社名名称",
                "マスター検索表示区分",
                "マスター検索表示区分名",
                "伝票出力区分",
                "伝票出力区分名",
                "変換コード伝票出力区分",
                "変換コード伝票出力区分名",
                "変換コード見積書出力区分",
                "変換コード見積書出力区分名",
                "変換コード請求書出力区分",
                "変換コード請求書出力区分名",
                "入力処理モード",
                "入力処理モード名",
                "個別設定入力行数",
                "社店コード",
                "分類コード",
                "伝票区分",
                "取引先コード",
                "有効期間開始日",
                "有効期間終了日",
                "操作日付",
                "ログインID",
                "ログイン名",
                "登録番号売上伝票出力",
                "登録番号売上伝票出力名",
                "登録番号請求書出力",
                "登録番号請求書出力名",
                "自社負担手数料入金後売上値引",
                "自社負担手数料入金後売上値引名"
        };
    }
}
