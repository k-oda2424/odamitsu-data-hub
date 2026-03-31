package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import jp.co.oda32.util.CollectionUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Smileから出力した売上明細ファイルの新規受注取込処理
 *
 * @author k_oda
 * @since 2024/05/13
 */
@Log4j2
@Component
public class NewSmileOrderProcessor {

    @Autowired
    SmileOrderImportService smileOrderImportService;
    long processedElements = 0;
    private int count = 0;
    private long totalElements = 0;

    public void newOrderProcess() throws Exception {
        int pageSize = 1000;
        int pageNumber = 0;

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<WSmileOrderOutputFile> page;
        do {
            page = smileOrderImportService.findNewOrders(pageable);
            if (pageNumber == 0) {
                this.totalElements = page.getTotalElements();
                log.info(String.format("全体の件数: %d件", totalElements));
            }

            List<WSmileOrderOutputFile> newOrderList = page.getContent();
            if (CollectionUtil.isEmpty(newOrderList)) {
                log.info("新規受注登録はありません");
                break;
            }
            log.info(String.format("新規受注登録: %d件, 現在処理中: %d/%d", newOrderList.size(), processedElements + newOrderList.size(), totalElements));

            this.processBatch(newOrderList);
            processedElements += newOrderList.size();
            pageNumber++;
            log.info(String.format("ページング %dページ目処理終了", pageNumber));
//            pageable = page.nextPageable();  // 次のページを取得
        } while (page.hasNext());  // 次のページが存在するか確認

        log.info("All pages processed. Total pages: " + page.getTotalPages());
    }

    public void processBatch(List<WSmileOrderOutputFile> newOrderList) throws Exception {
        // 前処理
        smileOrderImportService.preProcess(newOrderList);

        // 注文登録処理
        Map<ShoriRenbanKey, List<WSmileOrderOutputFile>> renbanMap = newOrderList.stream()
                .collect(Collectors.groupingBy(
                        order -> new ShoriRenbanKey(order.getShopNo(), order.getShoriRenban()),
                        Collectors.toList()
                ));

        List<Map.Entry<ShoriRenbanKey, List<WSmileOrderOutputFile>>> sortedEntries = new ArrayList<>(renbanMap.entrySet());
        sortedEntries.sort(Comparator.comparing((Map.Entry<ShoriRenbanKey, List<WSmileOrderOutputFile>> entry) -> entry.getKey().getShopNo())
                .thenComparing(entry -> entry.getKey().getShorirenban()));

        for (Map.Entry<ShoriRenbanKey, List<WSmileOrderOutputFile>> entry : sortedEntries) {
            ShoriRenbanKey key = entry.getKey();
            int entrySize = entry.getValue().size();
            if (entrySize == 1) {
                log.info(String.format("処理中 %d件中%d件目:", this.totalElements, this.count + 1));
            } else {
                log.info(String.format("処理中 %d件中%d～%d件目:", this.totalElements, this.count + 1, this.count + entrySize));
            }
            smileOrderImportService.newOrderRegister(key.getShopNo(), key.getShorirenban(), entry.getValue());
            this.count += entrySize;
        }
    }
}
