package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.util.PurchaseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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
        List<Integer> purchaseNoList = deleteTPurchaseDetailList.stream().map(TPurchaseDetail::getPurchaseNo).collect(Collectors.toList());
        for (TPurchaseDetail deletePurchaseDetail : deleteTPurchaseDetailList) {
            log.info(String.format("明細削除 処理連番:%d 行番号:%d", deletePurchaseDetail.getExtPurchaseNo(), deletePurchaseDetail.getPurchaseDetailNo()));
            List<TPurchaseDetail> confirmTPurchaseDetailList = this.tPurchaseDetailService.findByPurchaseNo(deletePurchaseDetail.getPurchaseNo());
            confirmTPurchaseDetailList.sort(Comparator.comparing(TPurchaseDetail::getPurchaseDetailNo));
            for (TPurchaseDetail confirm : confirmTPurchaseDetailList) {
                log.info(String.format("t_purchase_detail の処理連番:%d、行番号:%d、商品コード:%s、数量:%s%n"
                        , confirm.getExtPurchaseNo()
                        , confirm.getPurchaseDetailNo()
                        , confirm.getGoodsCode()
                        , confirm.getGoodsNum()));
            }
            this.tPurchaseDetailService.deletePermanently(deletePurchaseDetail);
        }
        // t_purchase再計算
        List<TPurchase> tPurchaseList = this.tPurchaseService.findByPurchaseNoList(purchaseNoList);
        for (TPurchase existPurchase : tPurchaseList) {
            PurchaseUtil.recalculateTPurchase(existPurchase, existPurchase.getPurchaseDetailList());
            this.tPurchaseService.update(existPurchase);
        }
    }

    /**
     * SMILEに存在しない仕入を検出し削除します。
     * これにより、SMILEシステム側で削除された仕入伝票も本システムから削除されます。
     *
     * @return 削除された仕入件数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int detectAndDeletePurchasesByDate() {
        log.info("SMILE削除仕入伝票検出処理開始");
        int deletedCount = 0;

        try {
            // クエリ実行前にログ出力
            log.info("削除対象仕入の検索を開始します");

            // 削除対象の仕入を取得
            List<TPurchase> purchasesToDelete = tPurchaseService.findDeletedPurchases(null);

            log.info("SMILE削除仕入検出: {} 件の削除対象仕入を検出しました", purchasesToDelete.size());

            // 削除対象がない場合は処理を終了
            if (purchasesToDelete.isEmpty()) {
                log.info("SMILE削除仕入検出処理終了: 削除対象仕入はありませんでした");
                return 0;
            }

            // 削除予定の仕入をログに出力
            purchasesToDelete.forEach(purchase -> {
                log.info("削除予定仕入: 仕入番号={}, ショップ番号={}, 仕入コード={}, SMILE処理連番={}, 仕入日={}",
                        purchase.getPurchaseNo(), purchase.getShopNo(), purchase.getPurchaseCode(),
                        purchase.getExtPurchaseNo(), purchase.getPurchaseDate());
            });

            // 各仕入を削除処理
            for (TPurchase purchase : purchasesToDelete) {
                Integer purchaseNo = purchase.getPurchaseNo();
                Integer shopNo = purchase.getShopNo();
                Long extPurchaseNo = purchase.getExtPurchaseNo();
                String purchaseCode = purchase.getPurchaseCode();

                log.info("SMILE削除仕入検出: ショップ番号={}, 仕入番号={}, 仕入コード={}, SMILE処理連番={}, 仕入日={}",
                        shopNo, purchaseNo, purchaseCode, extPurchaseNo, purchase.getPurchaseDate());

                // 仕入明細を削除
                List<TPurchaseDetail> details = tPurchaseDetailService.findByPurchaseNo(purchaseNo);
                for (TPurchaseDetail detail : details) {
                    try {
                        tPurchaseDetailService.deletePermanently(detail);
                        log.debug("SMILE削除仕入: 仕入明細を削除しました purchaseNo={}, detailNo={}, SMILE処理連番={}, 行番号={}",
                                detail.getPurchaseNo(), detail.getPurchaseDetailNo(), detail.getExtPurchaseNo(), detail.getPurchaseDetailNo());
                    } catch (Exception e) {
                        log.error("SMILE削除仕入: 仕入明細削除中にエラーが発生しました purchaseNo={}, detailNo={}, SMILE処理連番={}, 行番号={}",
                                detail.getPurchaseNo(), detail.getPurchaseDetailNo(), detail.getExtPurchaseNo(), detail.getPurchaseDetailNo(), e);
                    }
                }

                // 仕入を削除
                try {
                    tPurchaseService.deletePermanently(purchase);
                    log.info("SMILE削除仕入: 仕入を削除しました purchaseNo={}, purchaseCode={}, SMILE処理連番={}",
                            purchaseNo, purchaseCode, extPurchaseNo);
                    deletedCount++;
                } catch (Exception e) {
                    log.error("SMILE削除仕入: 仕入削除中にエラーが発生しました purchaseNo={}",
                            purchaseNo, e);
                }
            }

            log.info("SMILE削除仕入検出処理終了: {} 件の仕入を削除しました", deletedCount);

        } catch (Exception e) {
            log.error("SMILE削除仕入検出処理でエラーが発生しました", e);
        }

        return deletedCount;
    }
}
