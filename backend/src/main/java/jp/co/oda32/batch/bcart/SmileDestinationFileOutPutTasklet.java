package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.domain.model.bcart.DeliveryMapping;
import jp.co.oda32.domain.model.master.MShopLinkedFile;
import jp.co.oda32.domain.service.bcart.DeliveryMappingService;
import jp.co.oda32.domain.service.master.MShopLinkedFileService;
import jp.co.oda32.util.FileUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.springframework.batch.core.StepContribution;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
/**
 * B-Cartの配送先情報マスタをSmile用納品先マスタに登録するCSVファイル出力バッチ
 */
public class SmileDestinationFileOutPutTasklet implements Tasklet {

    private final DeliveryMappingService deliveryMappingService;
    @Autowired
    private MShopLinkedFileService mShopLinkedFileService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 連携していない行を取得
        List<DeliveryMapping> smileDeliveryNotLinkedList = this.deliveryMappingService.findBySmileCsvOutputtedFalse();
        MShopLinkedFile mShopLinkedFile = this.mShopLinkedFileService.getByShopNo(OfficeShopNo.B_CART_ORDER.getValue());
        if (mShopLinkedFile == null) {
            throw new Exception("SmileOrderFileOutPutTasklet：ショップ連携ファイルEntityが取得できませんでした。");
        }
        exportToCsv(smileDeliveryNotLinkedList, mShopLinkedFile.getSmileDestinationOutputFileName());
        return RepeatStatus.FINISHED;
    }

    private void exportToCsv(List<DeliveryMapping> deliveryMappingList, String outputFilePath) {
        if (StringUtil.isEmpty(outputFilePath)) {
            log.error("Smile用納品先マスタCSV出力バッチでファイル名の設定が空です。");
            return;
        }
        if (Files.exists(Paths.get(outputFilePath))) {
            FileUtil.renameCurrentFile(outputFilePath);
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withHeader("得意先コード", "納品先コード", "納品先名", "納品先索引", "荷受け人名１", "荷受け人名２", "郵便番号", "住所１", "住所２", "住所３", "電話番号", "ＦＡＸ番号", "マスター検索表示区分")
                .withQuoteMode(QuoteMode.ALL_NON_NULL)
                .withEscape('\\'); // set escape character

        try (FileOutputStream fos = new FileOutputStream(outputFilePath);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

            for (DeliveryMapping dm : deliveryMappingList) {
                csvPrinter.printRecord(
                        dm.getPartnerCode(),
                        dm.getSmileDeliveryCode(),
                        dm.getDeliveryName(),
                        dm.getDeliveryIndex(),
                        dm.getRecipientName1(),
                        dm.getRecipientName2(),
                        dm.getZip(),
                        dm.getAddress1(),
                        dm.getAddress2(),
                        dm.getAddress3(),
                        dm.getPhoneNumber(),
                        dm.getFaxNumber(),
                        0
                );
            }
            csvPrinter.flush();
            List<DeliveryMapping> updatedOutputList = deliveryMappingList.stream()
                    .peek(deliveryMapping -> deliveryMapping.setSmileCsvOutputted(true))
                    .collect(Collectors.toList());
            this.deliveryMappingService.saveAll(updatedOutputList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
