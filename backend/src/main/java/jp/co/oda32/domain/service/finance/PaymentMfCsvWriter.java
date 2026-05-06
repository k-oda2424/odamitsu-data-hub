package jp.co.oda32.domain.service.finance;

import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;
import jp.co.oda32.exception.FinanceInternalException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 買掛仕入 MF 仕訳 CSV の生成ロジック。
 *
 * <p>文字コードは CP932 (MS932)、改行は LF、金額末尾に半角スペースを付与する
 * 既存運用フォーマットに合わせる。取引日 (transactionDate) は CSV 全行共通で、
 * 小田光の締め日 (= 前月20日, transactionMonth) を渡す運用。送金日は MF の
 * 銀行データ連携側で自動付与されるため CSV には含めない。
 *
 * <p>ステートレスな純粋ユーティリティ。Bean 化せず static メソッドで提供する。
 */
final class PaymentMfCsvWriter {

    private PaymentMfCsvWriter() {}

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final Charset CSV_CHARSET = Charset.forName("MS932");
    private static final String CSV_LINE_END = "\n";

    private static final List<String> CSV_HEADERS = List.of(
            "取引No", "取引日", "借方勘定科目", "借方補助科目", "借方部門", "借方取引先",
            "借方税区分", "借方インボイス", "借方金額(円)",
            "貸方勘定科目", "貸方補助科目", "貸方部門", "貸方取引先",
            "貸方税区分", "貸方インボイス", "貸方金額(円)",
            "摘要", "タグ", "メモ"
    );

    /**
     * 仕訳行リストを CP932 (MS932) + LF の CSV バイト列に変換する。
     * {@code errorType=UNREGISTERED} の行はスキップ。
     *
     * <p>取引日は行ごとに {@link PaymentMfPreviewRow#getTransactionDate()} を優先し、
     * NULL のときは引数の fallback ({@code fallbackTransactionDate}) を使う。
     *
     * @param rows                    出力対象の仕訳行
     * @param fallbackTransactionDate 行に transactionDate が無い場合のフォールバック
     *                                （通常は締め日 = transactionMonth）
     * @return CSV バイト列（CP932 エンコード）
     */
    static byte[] toCsvBytes(List<PaymentMfPreviewRow> rows, LocalDate fallbackTransactionDate) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(baos, CSV_CHARSET)) {
            w.write(String.join(",", CSV_HEADERS));
            w.write(CSV_LINE_END);
            String fallback = fallbackTransactionDate == null ? "" : fallbackTransactionDate.format(CSV_DATE);
            for (PaymentMfPreviewRow r : rows) {
                if ("UNREGISTERED".equals(r.getErrorType())) continue;
                String date = r.getTransactionDate() != null
                        ? r.getTransactionDate().format(CSV_DATE) : fallback;
                w.write(toCsvLine(r, date));
                w.write(CSV_LINE_END);
            }
        } catch (IOException e) {
            // T5: I/O エラー (内部) を専用例外に置換。
            throw new FinanceInternalException("CSV出力に失敗しました", e);
        }
        return baos.toByteArray();
    }

    private static String toCsvLine(PaymentMfPreviewRow r, String date) {
        List<String> cols = new ArrayList<>(19);
        cols.add("");                                // 取引No
        cols.add(date);                              // 取引日
        cols.add(safe(r.getDebitAccount()));
        cols.add(safe(r.getDebitSubAccount()));
        cols.add(safe(r.getDebitDepartment()));
        cols.add("");                                // 借方取引先
        cols.add(safe(r.getDebitTax()));
        cols.add("");                                // 借方インボイス
        cols.add(fmtAmount(r.getDebitAmount()));
        cols.add(safe(r.getCreditAccount()));
        cols.add(safe(r.getCreditSubAccount()));
        cols.add(safe(r.getCreditDepartment()));
        cols.add("");                                // 貸方取引先
        cols.add(safe(r.getCreditTax()));
        cols.add("");                                // 貸方インボイス
        cols.add(fmtAmount(r.getCreditAmount()));
        cols.add(safe(r.getSummary()));
        cols.add(safe(r.getTag()));
        cols.add("");                                // メモ
        return String.join(",", cols);
    }

    private static String fmtAmount(Long v) {
        // 金額末尾の半角スペースは既存運用 CSV の不変条件。null 入力でも 0 + " " を返し、
        // 「列のうち1セルだけスペース欠落」が起きないようにする (SF-C02)。
        long amount = v == null ? 0L : v;
        return amount + " ";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
