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
@StepScope
public class NewSmilePurchaseProcessor {

    @Autowired
    SmilePurchaseImportService smilePurchaseImportService;

    private long processedCount = 0;
    private long totalElements = 0;

    public void newPurchaseProcess() throws Exception {
        log.info("=== 新規仕入取込処理を開始します ===");
        int pageSize = 1000;
        // 「未登録」条件の query で登録処理により結果セットが縮むため、page 0 を繰り返し取得する方式。
        // 通常のオフセット方式だと処理済み分だけ行がずれてレコードがスキップされる。
        Pageable pageable = PageRequest.of(0, pageSize);
        int iteration = 0;
        int maxIterations = 10000;
        long previousRemaining = Long.MAX_VALUE;
        while (iteration++ < maxIterations) {
            Page<WSmilePurchaseOutputFile> page = smilePurchaseImportService.findNewPurchases(pageable);
            long currentRemaining = page.getTotalElements();
            if (iteration == 1) {
                this.totalElements = currentRemaining;
                log.info("=== 全体の件数: {}件 ===", totalElements);
            }
            List<WSmilePurchaseOutputFile> newPurchaseList = page.getContent();
            if (CollectionUtil.isEmpty(newPurchaseList)) {
                if (iteration == 1) log.info("新規仕入登録はありません");
                break;
            }
            // 直前周の残件数 >= 今周の残件数 でないと、結果セットが縮んでおらず
            // 同じ行を永久にスキャンする無限ループに陥る。クエリ側のスキップ漏れを検知する保険。
            if (iteration > 1 && currentRemaining >= previousRemaining) {
                log.error("新規仕入取込で残件数が減少していません (前周={}, 今周={})。" +
                                "クエリで除外されていないスキップ対象行が存在する可能性があります。処理を中断します。",
                        previousRemaining, currentRemaining);
                break;
            }
            previousRemaining = currentRemaining;
            log.info("新規仕入登録: {}件 (累計処理: {}/{}件)", newPurchaseList.size(), processedCount + newPurchaseList.size(), totalElements);
            this.processBatch(newPurchaseList);
        }
        if (iteration >= maxIterations) {
            log.error("新規仕入取込が最大反復回数({})に達しました。データ不整合の可能性があります", maxIterations);
        }
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
