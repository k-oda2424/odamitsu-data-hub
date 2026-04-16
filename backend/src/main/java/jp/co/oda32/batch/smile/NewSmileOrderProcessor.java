package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import jp.co.oda32.util.CollectionUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Smileから出力した売上明細ファイルの新規受注取込処理
 *
 * @author k_oda
 * @since 2024/05/13
 */
@Log4j2
@Component
@StepScope
public class NewSmileOrderProcessor {

    @Autowired
    SmileOrderImportService smileOrderImportService;
    long processedElements = 0;
    private int count = 0;
    private long totalElements = 0;

    public void newOrderProcess() throws Exception {
        int pageSize = 1000;
        // 「未登録」条件の query で登録処理により結果セットが縮むため、page 0 を繰り返し取得する方式。
        // 同一 batch 内で登録処理が silent fail する行を無限に返し続けないよう、処理済みキー追跡で防御
        Pageable pageable = PageRequest.of(0, pageSize);
        int iteration = 0;
        int maxIterations = 10000;
        long totalProcessed = 0;
        Set<String> processedKeys = new HashSet<>();
        while (iteration++ < maxIterations) {
            Page<WSmileOrderOutputFile> page = smileOrderImportService.findNewOrders(pageable);
            if (iteration == 1) {
                this.totalElements = page.getTotalElements();
                log.info(String.format("全体の件数: %d件", totalElements));
            }
            List<WSmileOrderOutputFile> newOrderList = page.getContent();
            if (CollectionUtil.isEmpty(newOrderList)) {
                if (iteration == 1) log.info("新規受注登録はありません");
                break;
            }
            List<WSmileOrderOutputFile> unprocessedList = newOrderList.stream()
                    .filter(f -> !processedKeys.contains(makeKey(f)))
                    .collect(Collectors.toList());
            int skipped = newOrderList.size() - unprocessedList.size();
            if (skipped > 0) {
                log.warn("同一 batch 内で {} 行が再登場しました。登録処理が silent fail している可能性があります。スキップして継続します。", skipped);
            }
            if (unprocessedList.isEmpty()) {
                log.warn("新規受注取込: 未処理の行が無くなりましたがクエリは結果を返し続けています。データ異常として break します。");
                break;
            }
            totalProcessed += unprocessedList.size();
            log.info(String.format("新規受注登録: %d件 (累計処理: %d/%d)", unprocessedList.size(), totalProcessed, totalElements));
            this.processBatch(unprocessedList);
            unprocessedList.forEach(f -> processedKeys.add(makeKey(f)));
        }
        if (iteration >= maxIterations) {
            log.error("新規受注取込が最大反復回数({})に達しました。データ不整合の可能性があります", maxIterations);
        }
        log.info("All pages processed. Total processed: " + totalProcessed);
    }

    private String makeKey(WSmileOrderOutputFile f) {
        return f.getShopNo() + "_" + f.getShoriRenban() + "_" + f.getGyou();
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
