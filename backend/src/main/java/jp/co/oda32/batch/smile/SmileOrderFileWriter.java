package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import jp.co.oda32.domain.service.smile.WSmileOrderOutputFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import org.springframework.batch.item.Chunk;

import java.util.List;

/**
 * SMILE注文ファイルWriter
 *
 * @author k_oda
 * @since 2018/11/20
 */
@Component
@StepScope
@Log4j2
@RequiredArgsConstructor
public class SmileOrderFileWriter implements ItemWriter<SmileOrderFile> {
    private final WSmileOrderOutputFileService wSmileOrderOutputFileService;

    /**
     * Process the supplied data element. Will not be called with any null items
     * in normal operation.
     *
     * @param items items to be written
     * @throws Exception if there are errors. The framework will catch the
     *                   exception and convert or rethrow it as appropriate.
     */
    @Override
    public void write(Chunk<? extends SmileOrderFile> items) throws Exception {

        for (SmileOrderFile item : items) {
            log.info(String.format("伝票日付:%s,伝票番号:%s,明細番号:%s,得意先：%s,商品名：%s", item.get伝票日付(), item.get伝票番号(), item.get行(), item.get得意先名略称(), item.get商品名()));
            // Assuming WSmileOrderOutputFile entity with setSmileOrderFile method
            WSmileOrderOutputFile wSmileOrderOutputFile = new WSmileOrderOutputFile();
            wSmileOrderOutputFile = wSmileOrderOutputFile.convertSmileOrderFile(item);
            wSmileOrderOutputFileService.save(wSmileOrderOutputFile);
        }
    }

}
