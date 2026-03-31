package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.domain.model.bcart.TSmileOrderImportFile;
import jp.co.oda32.domain.model.master.MShopLinkedFile;
import jp.co.oda32.domain.service.bcart.TSmileOrderImportFileService;
import jp.co.oda32.domain.service.master.MShopLinkedFileService;
import jp.co.oda32.util.DateTimeUtil;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Smile連携用SmileOrderImportFileデータをCSV出力するタスクレットクラス
 *
 * @author k_oda
 * @since 2023/03/16
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class SmileOrderFileOutPutTasklet implements Tasklet {
    @Autowired
    private TSmileOrderImportFileService TSmileOrderImportFileService;
    @Autowired
    private MShopLinkedFileService mShopLinkedFileService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 連携していない行を取得
        List<TSmileOrderImportFile> TSmileOrderImportFileList = this.TSmileOrderImportFileService.findByCsvExportedFalse();
        MShopLinkedFile mShopLinkedFile = this.mShopLinkedFileService.getByShopNo(OfficeShopNo.B_CART_ORDER.getValue());
        if (mShopLinkedFile == null) {
            throw new Exception("SmileOrderFileOutPutTasklet：ショップ連携ファイルEntityが取得できませんでした。");
        }
        exportToCsv(TSmileOrderImportFileList, mShopLinkedFile.getSmileOrderOutputFileName());
        return RepeatStatus.FINISHED;
    }

    private void exportToCsv(List<TSmileOrderImportFile> outPutList, String outputFilePath) throws IOException {
        FileUtil.renameCurrentFile(outputFilePath);
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withHeader(SmileOrderImportCsvHeader.CSV_HEADERS)
                .withQuoteMode(QuoteMode.ALL_NON_NULL);

        // 伝票番号、明細番号で並べ替えをする。途中で軽減税率が混ざった場合、smileが別の連番を取得する仕様のため
        outPutList = outPutList.stream()
                .sorted(Comparator.comparing(TSmileOrderImportFile::getSlipNumber)
                        .thenComparing(TSmileOrderImportFile::getLineNumber))
                .collect(Collectors.toList());
        try (FileOutputStream fos = new FileOutputStream(outputFilePath);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

            for (TSmileOrderImportFile record : outPutList) {
                csvPrinter.printRecord(
                        DateTimeUtil.localDateToSlipDate(record.getSlipDate()), // 伝票日付
                        record.getSlipNumber(), // 伝票№
                        record.getProcessingSerialNumber(), // 処理連番
                        record.getDetailType(), // 明細区分
                        record.getLineNumber(), // 行
                        record.getCustomerCode(), // 得意先コード
                        record.getCustomerCompName(), // 得意先名１
                        record.getCustomerCompName2(), // 得意先名２
                        record.getDeliveryCode(), // 納品先コード
                        record.getDeliveryCompName(), // 納品先名
                        record.getPersonInChargeCode(), // 担当者コード
                        record.getBillingType(), // 請求区分
                        record.getAccountsReceivableType(), // 売掛区分
                        record.getTransactionType(), // 取引区分
                        record.getTransactionTypeAttribute(), // 取引区分属性
                        record.getProductCode(), // 商品コード
                        record.getProductName(), // 商品名
                        record.getSetQuantity(), // 入数
                        record.getOrderProCount(), // 個数
                        record.getCountUnit(), // 個数単位
                        record.getQuantity(), // 数量
                        record.getQuantityUnit(), // 数量単位
                        record.getUnitPrice(), // 単価
                        record.getAmount(), // 金額
                        record.getOriginalUnitPrice(), // 原単価
                        record.getCostAmount(), // 原価金額
                        record.getGrossMargin(), // 粗利
                        record.getUnitPriceMargin(), // 単価掛率
                        record.getTaxType(), // 課税区分
                        record.getTaxRate(), // 消費税率
                        record.getInternalConsumptionTax(), // 内消費税等
                        record.getLineSummaryCode(), // 行摘要コード
                        record.getLineSummary1(), // 行摘要１
                        record.getLineSummary2(), // 行摘要２
                        record.getRemarksCode(), // 備考コード
                        record.getRemarks(), // 備考
                        record.getOrderNumber(), // 受注№
                        record.getOrderLine(), // 受注行
                        record.getQuoteProcessingSerialNumber(), // 見積処理連番
                        record.getQuoteLine(), // 見積行
                        record.getAutoGeneratedType(), // 自動生成区分
                        record.getSlipConsumptionTaxCalculationType(), // 伝票消費税計算区分
                        record.getDataOccurrenceType(), // データ発生区分
                        record.getCounterpartProcessingSerialNumber(), // 相手処理連番
                        record.getInputPatternNumber(), // 入力ﾊﾟﾀｰﾝ№
                        record.getSlipSerialNumber(), // 伝票番号
                        record.getCounterpartSlipSerialNumber(), // 相手伝票番号
                        record.getCode1(), // コード
                        record.getCode2(), // コード
                        record.getCompanyStoreCode(), // 社店コード
                        record.getClassificationCode(), // 分類コード
                        record.getSlipType(), // 伝票区分
                        record.getTradingPartnerCode(), // 取引先コード
                        record.getSellingUnitPrice(), // 売単価
                        record.getCounterpartProductCode(), // 相手商品コード
                        record.getCheckMarkType(), // チェックマーク区分
                        record.getConsumptionTaxClassification() // 消費税分類
                );
            }
            csvPrinter.flush();
            List<TSmileOrderImportFile> updatedOutputList = outPutList.stream()
                    .peek(smileOrderImportFile -> smileOrderImportFile.setCsvExported(true))
                    .collect(Collectors.toList());
            this.TSmileOrderImportFileService.save(updatedOutputList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
