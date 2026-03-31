package jp.co.oda32.batch.bcart;

import lombok.Data;

/**
 * B-Cart出荷実績インポート用CSVファイル定義
 *
 * @author k_oda
 * @since 2023/04/20
 */
@Data
public class BCartLogisticsCsv {
    public static final String[] CSV_HEADERS = {
            "Bカート発送ID", "送り状番号", "発送日", "出荷管理番号", "発送状況", "発送メモ", "お客様への連絡事項", "対応状況"
    };
}
