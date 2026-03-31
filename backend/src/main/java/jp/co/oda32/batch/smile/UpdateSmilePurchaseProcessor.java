package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
import jp.co.oda32.util.CollectionUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Smile仕入明細データから更新されたデータを処理するクラス
 *
 * @author
 * @since 2024/05/13
 */
@Log4j2
@Component
public class UpdateSmilePurchaseProcessor {

    @Autowired
    SmilePurchaseUpdateService smilePurchaseUpdateService;

    private long processedCount = 0;
    private long totalElements = 0;

    public void modifiedPurchaseProcess() throws Exception {
        log.info("=== 仕入更新処理を開始します ===");
        int pageSize = 1000;
        int pageNumber = 0;

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<WSmilePurchaseOutputFile> page;
        do {
            page = smilePurchaseUpdateService.findModifiedPurchases(pageable);
            if (pageNumber == 0) {
                this.totalElements = page.getTotalElements();
                log.info("=== 全体の件数: {}件 ===", totalElements);
            }
            List<WSmilePurchaseOutputFile> updatePurchaseList = page.getContent();
            if (CollectionUtil.isEmpty(updatePurchaseList)) {
                log.info("仕入更新はありません");
                break;
            }

            log.info("仕入更新: {}件, 現在処理中: {}/{}件", updatePurchaseList.size(), processedCount + updatePurchaseList.size(), totalElements);

            // 前処理
            smilePurchaseUpdateService.preProcess(updatePurchaseList);

            // 更新処理
            this.updateModifiedPurchase(updatePurchaseList);

            // ページインクリメント
            pageNumber++;
            log.info("ページング {}ページ目処理終了", pageNumber);

            // 次のページを取得
            pageable = page.nextPageable();

        } while (page.hasNext());  // 次のページが存在するか確認

        log.info("=== 全ページの処理が完了しました。合計{}ページ ===", page.getTotalPages());
        log.info("=== 仕入更新処理が完了しました。合計{}件を処理しました。 ===", processedCount);
    }

    private void updateModifiedPurchase(List<WSmilePurchaseOutputFile> modifiedPurchaseList) throws Exception {
        // 処理連番とショップ番号の組み合わせでグルーピング
        Map<String, List<WSmilePurchaseOutputFile>> renbanAndShopNoMap = modifiedPurchaseList.stream()
                .collect(Collectors.groupingBy(
                        file -> file.getShoriRenban() + "_" + file.getShopNo()
                ));

        for (Map.Entry<String, List<WSmilePurchaseOutputFile>> entry : renbanAndShopNoMap.entrySet()) {
            String[] keys = entry.getKey().split("_");
            Long shoriRenban = Long.parseLong(keys[0]); // 処理連番
            int shopNo = Integer.parseInt(keys[1]);     // ショップ番号
            int entrySize = entry.getValue().size();

            // 進捗の更新と表示
            updateProgress(entrySize);

            try {
                smilePurchaseUpdateService.updatePurchase(shopNo, shoriRenban, entry.getValue());
            } catch (Exception e) {
                log.error("エラーが発生しました。店舗番号: {}, 処理連番: {}, エントリーサイズ: {}", shopNo, shoriRenban, entrySize, e);
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
