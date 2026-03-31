package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.util.CollectionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
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
public class DeleteSmilePurchaseProcessor {

    @Autowired
    SmilePurchaseDeleteService smilePurchaseDeleteService;

    public void deletePurchaseProcess() throws Exception {
        // SMILE削除仕入検出処理（伝票日付ごとに存在しない仕入伝票を検出して削除）
        int deleteCount = smilePurchaseDeleteService.detectAndDeletePurchasesByDate();
        log.info("SMILE削除仕入検出処理: {} 件の仕入を削除しました", deleteCount);

        // 削除対象明細レコード（個別の明細行が削除された場合の処理）
        List<TPurchaseDetail> deletTPurchaseDetailList = smilePurchaseDeleteService.findDeletTPurchaseDetailList();
        if (CollectionUtil.isEmpty(deletTPurchaseDetailList)) {
            log.info("削除対象明細はありませんでした。");
            return;
        }
        log.info(String.format("削除対象明細%d件", deletTPurchaseDetailList.size()));
        // 明細削除処理
        smilePurchaseDeleteService.deletePurchase(deletTPurchaseDetailList);
    }
}
