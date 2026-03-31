package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
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
 * Smileから出力した売上明細ファイルの新規仕入取込処理
 *
 * @author k_oda
 * @since 2024/09/11
 */
@Log4j2
@Component
public class NewSmilePurchaseProcessor {

    @Autowired
    SmilePurchaseImportService smilePurchaseImportService;

    private long processedCount = 0;
    private long totalElements = 0;

    public void newPurchaseProcess() throws Exception {
        log.info("=== 新規仕入取込処理を開始します ===");
        int pageSize = 1000;
        int pageNumber = 0;

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<WSmilePurchaseOutputFile> page;
        do {
            page = smilePurchaseImportService.findNewPurchases(pageable);
            if (pageNumber == 0) {
                this.totalElements = page.getTotalElements();
                log.info("=== 全体の件数: {}件 ===", totalElements);
            }

            List<WSmilePurchaseOutputFile> newPurchaseList = page.getContent();
            if (CollectionUtil.isEmpty(newPurchaseList)) {
                log.info("新規仕入登録はありません");
                break;
            }
            log.info("新規仕入登録: {}件, 現在処理中: {}/{}件", newPurchaseList.size(), processedCount + newPurchaseList.size(), totalElements);

            this.processBatch(newPurchaseList);
            pageNumber++;
            log.info("ページング {}ページ目処理終了", pageNumber);
            pageable = page.nextPageable();  // 次のページを取得
        } while (page.hasNext());  // 次のページが存在するか確認

        log.info("=== 全ページの処理が完了しました。合計{}ページ ===", page.getTotalPages());
        log.info("=== 新規仕入取込処理が完了しました。合計{}件を処理しました。 ===", processedCount);
    }

    private void processBatch(List<WSmilePurchaseOutputFile> newPurchaseList) throws Exception {
        // 前処理
        smilePurchaseImportService.preProcess(newPurchaseList);

        // 仕入登録処理
        Map<ShoriRenbanKey, List<WSmilePurchaseOutputFile>> renbanMap = newPurchaseList.stream()
                .collect(Collectors.groupingBy(
                        purchase -> new ShoriRenbanKey(purchase.getShopNo(), purchase.getShoriRenban()),
                        Collectors.toList()
                ));

        List<Map.Entry<ShoriRenbanKey, List<WSmilePurchaseOutputFile>>> sortedEntries = new ArrayList<>(renbanMap.entrySet());
        sortedEntries.sort(Comparator.comparing((Map.Entry<ShoriRenbanKey, List<WSmilePurchaseOutputFile>> entry) -> entry.getKey().getShopNo())
                .thenComparing(entry -> entry.getKey().getShorirenban()));

        for (Map.Entry<ShoriRenbanKey, List<WSmilePurchaseOutputFile>> entry : sortedEntries) {
            ShoriRenbanKey key = entry.getKey();
            int entrySize = entry.getValue().size();

            // 進捗の更新と表示
            updateProgress(entrySize);

            try {
                smilePurchaseImportService.newPurchaseRegister(key.getShopNo(), key.getShorirenban(), entry.getValue());
            } catch (Exception e) {
                log.error("エラーが発生しました。店舗番号: {}, 処理連番: {}, エントリーサイズ: {}", key.getShopNo(), key.getShorirenban(), entrySize, e);
                throw e; // 必要に応じて再スロー
            }
        }
    }

    private void updateProgress(int entrySize) {
        processedCount += entrySize;
        double progressPercentage = ((double) processedCount / totalElements) * 100;
        String formattedPercentage = String.format("%.2f", progressPercentage);
        log.info("処理中: {}/{}件 ({}%完了)", processedCount, totalElements, formattedPercentage);

    }
}
