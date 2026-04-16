package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * smile売上明細データから更新されたデータを処理するクラス
 *
 * <p>{@link SmileOrderUpdateService#findModifiedOrders} は「値が異なる」行を返すため、
 * 登録処理後に結果セットが縮まない可能性がある（例: w_smile に同一処理連番で
 * 異なる denpyou_hizuke が混在するデータ異常時など）。
 * 同一 batch 内で 1 つの {@code (shop_no, shori_renban)} は **最大 1 回だけ** 処理するよう
 * 追跡集合を持ち、2 度目の出現時は data anomaly としてスキップ + 警告する。
 *
 * @author k_oda
 * @since 2024/05/13
 */
@Log4j2
@Component
@StepScope
public class UpdateSmileOrderProcessor {
    @Autowired
    SmileOrderUpdateService smileOrderUpdateService;

    public void modifiedOrderProcess() throws Exception {
        int pageSize = 1000;
        int iteration = 0;
        int maxIterations = 10000;
        long totalProcessed = 0;
        // 処理済み (shop_no, shori_renban) を追跡し、再登場時はスキップ（無限ループ防御）
        Set<String> processedKeys = new HashSet<>();
        while (iteration++ < maxIterations) {
            List<WSmileOrderOutputFile> modifiedOrderList =
                    this.smileOrderUpdateService.findModifiedOrders(pageSize, 0);
            if (modifiedOrderList.isEmpty()) {
                break;
            }
            // 既に処理済みの shori_renban を除外
            List<WSmileOrderOutputFile> unprocessedList = modifiedOrderList.stream()
                    .filter(f -> !processedKeys.contains(makeKey(f)))
                    .collect(Collectors.toList());
            int skippedCount = modifiedOrderList.size() - unprocessedList.size();
            if (skippedCount > 0) {
                // 既処理の行が再登場している = 通常あり得ない（w_smile のデータ異常の可能性大）
                Set<String> anomalyKeys = modifiedOrderList.stream()
                        .map(this::makeKey)
                        .filter(processedKeys::contains)
                        .collect(Collectors.toSet());
                log.warn("同一 batch 内で {} 行が再処理要求されました。w_smile_order_output_file のデータ異常の可能性があります。対象: {} 件のキー ({})",
                        skippedCount, anomalyKeys.size(), anomalyKeys);
            }
            if (unprocessedList.isEmpty()) {
                // 今回の page がすべて処理済みキーのみ → データ異常で進めないため break
                log.warn("受注更新: 未処理の行が無くなりましたが、クエリは差分を返し続けています。データ異常として break します。");
                break;
            }
            totalProcessed += unprocessedList.size();
            log.info(String.format("受注情報更新中:%d件 (累計 %d件)", unprocessedList.size(), totalProcessed));
            smileOrderUpdateService.preProcess(unprocessedList);
            smileOrderUpdateService.updatePartner(unprocessedList);
            updateModifiedOrder(unprocessedList);
            // 処理済みキーを記録
            unprocessedList.forEach(f -> processedKeys.add(makeKey(f)));
        }
        if (iteration >= maxIterations) {
            log.error("受注更新処理が最大反復回数({})に達しました。データ不整合の可能性があります", maxIterations);
        }
    }

    private String makeKey(WSmileOrderOutputFile f) {
        return f.getShopNo() + "_" + f.getShoriRenban() + "_" + f.getGyou();
    }

    private void updateModifiedOrder(List<WSmileOrderOutputFile> modifiedOrderList) throws Exception {
        // modifiedOrderListから処理連番とショップ番号の組み合わせでまとまりのMapを作成する
        Map<String, List<WSmileOrderOutputFile>> renbanAndShopNoMap = modifiedOrderList.stream()
                .collect(Collectors.groupingBy(
                        file -> file.getShoriRenban() + "_" + file.getShopNo()
                ));

        // renbanAndShopNoMapをforでまわす
        for (Map.Entry<String, List<WSmileOrderOutputFile>> entry : renbanAndShopNoMap.entrySet()) {
            String[] keys = entry.getKey().split("_");
            Long shoriRenban = Long.parseLong(keys[0]); // 処理連番
            int shopNo = Integer.parseInt(keys[1]); // ショップ番号
            // 必要に応じてshopNoも使用して処理を行う
            smileOrderUpdateService.updateOrder(shopNo, shoriRenban, entry.getValue());
        }
    }

}
