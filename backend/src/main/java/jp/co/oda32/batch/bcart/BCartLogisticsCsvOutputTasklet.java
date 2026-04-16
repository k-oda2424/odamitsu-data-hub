package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.domain.model.bcart.BCartLogistics;
import jp.co.oda32.domain.model.bcart.BCartOrder;
import jp.co.oda32.domain.model.bcart.BCartOrderProduct;
import jp.co.oda32.domain.model.master.MShopLinkedFile;
import jp.co.oda32.domain.service.bcart.BCartLogisticsService;
import jp.co.oda32.domain.service.bcart.BCartOrderProductService;
import jp.co.oda32.domain.service.master.MShopLinkedFileService;
import jp.co.oda32.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * B-Cart連携用LogisticsデータをCSV出力するタスクレットクラス
 *
 * @author k_oda
 * @since 2023/04/20
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartLogisticsCsvOutputTasklet implements Tasklet {
    private final BCartLogisticsService bCartLogisticsService;
    private final BCartOrderProductService bCartOrderProductService;
    private final MShopLinkedFileService mShopLinkedFileService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // csv出力をしていない、かつ発送状況が「発送指示」または「発送済」
        // csv出力をしている、かつis_updated:true「更新」のもの
        List<BCartLogistics> exportBCartLogisticsList = this.bCartLogisticsService.findExportableRecords();

        // 一括でBCartOrderを取得するための前処理
        List<Long> logisticsIds = exportBCartLogisticsList.stream()
                .map(BCartLogistics::getId)
                .distinct()
                .collect(Collectors.toList());

        // 全てのBCartOrderProductを一度に取得（logisticsIdに基づく）
        List<BCartOrderProduct> allOrderProducts = bCartOrderProductService.findByLogisticsIdIn(logisticsIds);

        // BCartOrderのマップを作成（キー: logisticsId, 値: 対応するBCartOrder）
        Map<Long, BCartOrder> orderMap = new HashMap<>();
        for (BCartOrderProduct orderProduct : allOrderProducts) {
            if (orderProduct.getBCartOrder() != null && !orderMap.containsKey(orderProduct.getLogisticsId())) {
                orderMap.put(orderProduct.getLogisticsId(), orderProduct.getBCartOrder());
            }
        }
        
        MShopLinkedFile mShopLinkedFile = this.mShopLinkedFileService.getByShopNo(OfficeShopNo.B_CART_ORDER.getValue());
        if (mShopLinkedFile == null) {
            throw new Exception("SmileOrderFileOutPutTasklet：ショップ連携ファイルEntityが取得できませんでした。");
        }
        exportToCsv(exportBCartLogisticsList, mShopLinkedFile.getBCartLogisticsImportFileName(), orderMap);
        return RepeatStatus.FINISHED;
    }

    private void exportToCsv(List<BCartLogistics> outPutList, String outputFilePath, Map<Long, BCartOrder> orderMap) throws IOException {
        FileUtil.renameCurrentFile(outputFilePath);
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withHeader(BCartLogisticsCsv.CSV_HEADERS)
                .withQuoteMode(QuoteMode.ALL_NON_NULL);

        // CSV 出力成功後にのみフラグ更新 & save する。書込中の例外は throw して step transaction をロールバック。
        try (FileOutputStream fos = new FileOutputStream(outputFilePath);
             OutputStreamWriter osw = new OutputStreamWriter(fos, Charset.forName("Shift_JIS"));
             BufferedWriter writer = new BufferedWriter(osw);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

            for (BCartLogistics record : outPutList) {
                // マップから注文情報を取得（紐付かない logistics に備えて null ガード）
                BCartOrder bCartOrder = orderMap.get(record.getId());
                String adminMessage = bCartOrder != null ? bCartOrder.getAdminMessage() : null;
                String orderStatus = bCartOrder != null ? bCartOrder.getStatus() : null;
                if (bCartOrder == null) {
                    log.warn("BCartLogistics id={} に紐づく BCartOrder が見つかりません。連絡事項・対応状況を空でCSV出力します。", record.getId());
                }

                // "Bカート発送ID", "送り状番号", "発送日", "出荷管理番号", "発送状況", "発送メモ", "お客様への連絡事項", "対応状況"
                csvPrinter.printRecord(
                        record.getId(),// Bカート発送ID
                        record.getDeliveryCode(),// 送り状番号
                        record.getShipmentDate(),//発送日
                        record.getShipmentCode(),//出荷管理番号(SMILE処理連番)
                        record.getStatus(),//発送状況
                        record.getMemo(),// 発送メモ
                        adminMessage, // お客様への連絡事項
                        orderStatus   // 対応状況
                );
            }
            csvPrinter.flush();
        }
        // CSV 書き出しが成功した場合のみフラグを更新して保存
        List<BCartLogistics> updatedOutputList = outPutList.stream()
                .peek(logistics -> logistics.setUpdated(false))
                .peek(logistics -> logistics.setBCartCsvExported(true))
                .collect(Collectors.toList());
        this.bCartLogisticsService.save(updatedOutputList);
    }
}
