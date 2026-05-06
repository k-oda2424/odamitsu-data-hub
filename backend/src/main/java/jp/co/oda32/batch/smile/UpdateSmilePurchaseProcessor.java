package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
import jp.co.oda32.util.CollectionUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Smile仕入明細データから更新されたデータを処理するクラス
 *
 * @author
 * @since 2024/05/13
 */
@Log4j2
@Component
@StepScope
public class UpdateSmilePurchaseProcessor {

    @Autowired
    SmilePurchaseUpdateService smilePurchaseUpdateService;

    private long processedCount = 0;
    private long totalElements = 0;

    public void modifiedPurchaseProcess() throws Exception {
        log.info("=== 仕入更新処理を開始します ===");
        int pageSize = 1000;
        // 「値が異なる」条件の query で更新処理により結果セットが縮むため、page 0 を繰り返し取得する。
        // 同一 batch 内で処理済みの (shop_no, shori_renban, gyou) は再処理しない（データ異常による無限ループ防御）
        Pageable pageable = PageRequest.of(0, pageSize);
        int iteration = 0;
        int maxIterations = 10000;
        Set<String> processedKeys = new HashSet<>();
        while (iteration++ < maxIterations) {
            Page<WSmilePurchaseOutputFile> page = smilePurchaseUpdateService.findModifiedPurchases(pageable);
            if (iteration == 1) {
                this.totalElements = page.getTotalElements();
                log.info("=== 全体の件数: {}件 ===", totalElements);
            }
            List<WSmilePurchaseOutputFile> updatePurchaseList = page.getContent();
            if (CollectionUtil.isEmpty(updatePurchaseList)) {
                if (iteration == 1) log.info("仕入更新はありません");
                break;
            }
            // 既に処理済みの行を除外
            List<WSmilePurchaseOutputFile> unprocessedList = updatePurchaseList.stream()
                    .filter(f -> !processedKeys.contains(makeKey(f)))
                    .collect(Collectors.toList());
            int skippedCount = updatePurchaseList.size() - unprocessedList.size();
            if (skippedCount > 0) {
                log.warn("同一 batch 内で {} 行が再処理要求されました。w_smile_purchase_output_file のデータ異常の可能性があります。", skippedCount);
            }
            if (unprocessedList.isEmpty()) {
                log.warn("仕入更新: 未処理の行が無くなりましたが、クエリは差分を返し続けています。データ異常として break します。");
                break;
            }
            log.info("仕入更新: {}件 (累計 {}/{}件)", unprocessedList.size(), processedCount + unprocessedList.size(), totalElements);
            smilePurchaseUpdateService.preProcess(unprocessedList);
            this.updateModifiedPurchase(unprocessedList);
            unprocessedList.forEach(f -> processedKeys.add(makeKey(f)));
        }
        if (iteration >= maxIterations) {
            log.error("仕入更新処理が最大反復回数({})に達しました。データ不整合の可能性があります", maxIterations);
        }
        log.info("=== 仕入更新処理が完了しました。合計{}件を処理しました。 ===", processedCount);
    }

    private String makeKey(WSmilePurchaseOutputFile f) {
        return f.getShopNo() + "_" + f.getShoriRenban() + "_" + f.getGyou();
    }

    private void updateModifiedPurchase(List<WSmilePurchaseOutputFile> modifiedPurchaseList) throws Exception {
        // 処理連番とショップ番号の組み合わせでグルーピング
        Map<ShoriRenbanKey, List<WSmilePurchaseOutputFile>> renbanMap = modifiedPurchaseList.stream()
                .collect(Collectors.groupingBy(
                        file -> new ShoriRenbanKey(file.getShopNo(), file.getShoriRenban())
                ));

        for (Map.Entry<ShoriRenbanKey, List<WSmilePurchaseOutputFile>> entry : renbanMap.entrySet()) {
            ShoriRenbanKey key = entry.getKey();
            int entrySize = entry.getValue().size();

            // 進捗の更新と表示
            updateProgress(entrySize);

            try {
                smilePurchaseUpdateService.updatePurchase(key.getShopNo(), key.getShorirenban(), entry.getValue());
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
