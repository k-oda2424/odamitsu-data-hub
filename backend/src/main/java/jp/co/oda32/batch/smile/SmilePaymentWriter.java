package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmilePayment;
import jp.co.oda32.domain.service.smile.TSmilePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.batch.item.Chunk;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SMILE支払情報のライタークラス
 * 処理されたデータをデータベースに保存します
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@Component
@StepScope
@Log4j2
@RequiredArgsConstructor
public class SmilePaymentWriter implements ItemWriter<WSmilePayment> {

    private final TSmilePaymentService tSmilePaymentService;

    /**
     * 支払明細データを保存します
     *
     * @param items 保存するWSmilePaymentエンティティのリスト
     */
    @Override
    @Transactional
    public void write(Chunk<? extends WSmilePayment> items) {
        if (items.isEmpty()) {
            log.info("保存対象のデータがありません");
            return;
        }

        // null以外のアイテムのみをフィルタリング
        List<WSmilePayment> validItems = items.getItems().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (validItems.isEmpty()) {
            log.info("保存対象の有効なデータがありません");
            return;
        }

        try {
            // ワークテーブルにデータを保存
            List<WSmilePayment> savedItems = tSmilePaymentService.saveAllToWorkTable(validItems);
            log.info("ワークテーブルにSMILE支払情報を登録しました。件数: {}", savedItems.size());

            // 伝票日付ごとにグループ化して処理
            Map<LocalDate, List<WSmilePayment>> paymentsByDate = new HashMap<>();

            for (WSmilePayment payment : validItems) {
                LocalDate voucherDate = payment.getVoucherDate();
                if (voucherDate != null) {
                    paymentsByDate.computeIfAbsent(voucherDate, k -> new ArrayList<>()).add(payment);
                } else {
                    // 伝票日付がnullの場合は別途記録
                    log.warn("伝票日付がnullのデータがあります: {}", payment.getProcessingSerialNumber());
                }
            }

            // 伝票日付ごとにデータ同期を実行
            for (LocalDate voucherDate : paymentsByDate.keySet()) {
                tSmilePaymentService.synchronizePaymentData(voucherDate);
                log.info("伝票日付: {}のデータを同期しました", voucherDate);
            }
        } catch (Exception e) {
            log.error("SMILE支払情報の保存中にエラーが発生しました", e);
            throw e;
        }
    }
}
