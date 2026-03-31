package jp.co.oda32.batch.finance;

import lombok.Builder;
import lombok.Data;

/**
 * マネーフォワード仕訳帳のCSVフォーマットを定義するクラス
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Data
@Builder
public class MFJournalCsv {

    public static final String CSV_HEADER = "\"取引No\",\"取引日\",\"借方勘定科目\",\"借方補助科目\",\"借方部門\",\"借方取引先\",\"借方税区分\",\"借方インボイス\",\"借方金額(円)\",\"貸方勘定科目\",\"貸方補助科目\",\"貸方部門\",\"貸方取引先\",\"貸方税区分\",\"貸方インボイス\",\"貸方金額(円)\",\"摘要\",\"タグ\",\"メモ\"";

    private String transactionNo;           // 取引No
    private String transactionDate;         // 取引日
    private String debitAccount;            // 借方勘定科目
    private String debitSubAccount;         // 借方補助科目
    private String debitDepartment;         // 借方部門
    private String debitPartner;            // 借方取引先
    private String debitTaxCategory;        // 借方税区分
    private String debitInvoice;            // 借方インボイス
    private String debitAmount;             // 借方金額(円)
    private String creditAccount;           // 貸方勘定科目
    private String creditSubAccount;        // 貸方補助科目
    private String creditDepartment;        // 貸方部門
    private String creditPartner;           // 貸方取引先
    private String creditTaxCategory;       // 貸方税区分
    private String creditInvoice;           // 貸方インボイス
    private String creditAmount;            // 貸方金額(円)
    private String summary;                 // 摘要
    private String tag;                     // タグ
    private String memo;                    // メモ
}
