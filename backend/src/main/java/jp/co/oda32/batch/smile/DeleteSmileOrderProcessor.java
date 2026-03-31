package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.util.CollectionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 削除されたレコードを本システムからも削除します。
 *
 * @author k_oda
 * @since 2024/05/13
 */
@Log4j2
@RequiredArgsConstructor
@Component
public class DeleteSmileOrderProcessor {

    @Autowired
    SmileOrderDeletionProcessor smileOrderDeletionProcessor;
    @Value("${smile.shop-no:1}")
    private Integer defaultShopNo;

    public void deleteOrderProcess() throws Exception {
        // SMILE注文取込処理の後に追加する部分
        // SMILE削除注文検出処理（注文全体削除）
        int deleteCount = smileOrderDeletionProcessor.detectAndDeleteOrders(defaultShopNo);
        log.info("SMILE削除注文検出処理: {} 件の注文を削除しました", deleteCount);
        // 削除対象レコード
        List<TDeliveryDetail> deleteTDeliveryDetailList = smileOrderDeletionProcessor.findDeletTDeliveryList();
        if (CollectionUtil.isEmpty(deleteTDeliveryDetailList)) {
            log.info("削除対象はありませんでした。");
            return;
        }
        log.info(String.format("削除対象%d件", deleteTDeliveryDetailList.size()));
        // 明細削除処理
        smileOrderDeletionProcessor.deleteOrder(deleteTDeliveryDetailList);

    }

}
