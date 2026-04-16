package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.util.PurchaseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SMILEからの受注データを削除処理するクラス
 * トランザクションをかけるメソッドはこのクラスに記載する
 *
 * @author k_oda
 * @since 2024/05/20
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class SmilePurchaseDeleteService extends AbstractSmilePurchaseImportService {

    public List<TPurchaseDetail> findDeletTPurchaseDetailList() {
        return this.tPurchaseDetailService.findDeletTPurchaseDetailList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deletePurchase(List<TPurchaseDetail> deleteTPurchaseDetailList) throws Exception {
        List<Integer> purchaseNoList = deleteTPurchaseDetailList.stream()
                .map(TPurchaseDetail::getPurchaseNo)
                .distinct()
                .collect(Collectors.toList());
        for (TPurchaseDetail deletePurchaseDetail : deleteTPurchaseDetailList) {
            log.info(String.format("明細削除 処理連番:%d 行番号:%d", deletePurchaseDetail.getExtPurchaseNo(), deletePurchaseDetail.getPurchaseDetailNo()));
            this.tPurchaseDetailService.deletePermanently(deletePurchaseDetail);
        }
        // t_purchase再計算: Hibernate collection cache は物理削除後に自動更新されないため、
        // 明細を明示的に再取得してから再計算する（stale data 防止）
        List<TPurchase> tPurchaseList = this.tPurchaseService.findByPurchaseNoList(purchaseNoList);
        for (TPurchase existPurchase : tPurchaseList) {
            List<TPurchaseDetail> latestDetails =
                    this.tPurchaseDetailService.findByPurchaseNo(existPurchase.getPurchaseNo());
            PurchaseUtil.recalculateTPurchase(existPurchase, latestDetails);
            this.tPurchaseService.update(existPurchase);
        }
    }

    /**
     * SMILEに存在しない仕入を検出し削除します。
     * これにより、SMILEシステム側で削除された仕入伝票も本システムから削除されます。
     * 削除中のエラーは例外として伝播させ、@Transactional によるロールバックで partial commit を防ぎます。
     *
     * @return 削除された仕入件数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int detectAndDeletePurchasesByDate() throws Exception {
        log.info("SMILE削除仕入伝票検出処理開始");
        int deletedCount = 0;

        // 削除対象の仕入を取得
        List<TPurchase> purchasesToDelete = tPurchaseService.findDeletedPurchases(null);
        log.info("SMILE削除仕入検出: {} 件の削除対象仕入を検出しました", purchasesToDelete.size());

        if (purchasesToDelete.isEmpty()) {
            log.info("SMILE削除仕入検出処理終了: 削除対象仕入はありませんでした");
            return 0;
        }

        for (TPurchase purchase : purchasesToDelete) {
            Integer purchaseNo = purchase.getPurchaseNo();
            Integer shopNo = purchase.getShopNo();
            Long extPurchaseNo = purchase.getExtPurchaseNo();
            String purchaseCode = purchase.getPurchaseCode();

            log.info("SMILE削除仕入: ショップ番号={}, 仕入番号={}, 仕入コード={}, SMILE処理連番={}, 仕入日={}",
                    shopNo, purchaseNo, purchaseCode, extPurchaseNo, purchase.getPurchaseDate());

            // 仕入明細を削除（例外は throw されてトランザクションロールバック）
            List<TPurchaseDetail> details = tPurchaseDetailService.findByPurchaseNo(purchaseNo);
            for (TPurchaseDetail detail : details) {
                tPurchaseDetailService.deletePermanently(detail);
            }

            // 仕入伝票を削除
            tPurchaseService.deletePermanently(purchase);
            deletedCount++;
        }

        log.info("SMILE削除仕入検出処理終了: {} 件の仕入を削除しました", deletedCount);
        return deletedCount;
    }
}
