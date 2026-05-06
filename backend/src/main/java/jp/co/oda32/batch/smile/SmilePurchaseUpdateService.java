package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
import jp.co.oda32.util.BigDecimalUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SMILEからの仕入データを更新処理するクラス
 * トランザクションをかけるメソッドはこのクラスに記載する
 *
 * @author k_oda
 * @since 2024/05/20
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class SmilePurchaseUpdateService extends AbstractSmilePurchaseImportService {

    public Page<WSmilePurchaseOutputFile> findModifiedPurchases(Pageable firstPageable) {
        return wSmilePurchaseOutputFileService.findModifiedPurchases(firstPageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePurchase(int shopNo, Long shorirenban, List<WSmilePurchaseOutputFile> modifiedPurchaseFileList) throws Exception {
        // 仕入・仕入明細
        TPurchase existPurchase = this.tPurchaseService.getByUniqKey(shopNo, shorirenban);
        if (existPurchase == null) {
            log.error(String.format("仕入更新処理でt_purhaseが見つかりません。shop_no:%d 処理連番:%d", shopNo, shorirenban));
            return;
        }
        List<TPurchaseDetail> tPurchaseDetailList = existPurchase.getPurchaseDetailList();
        // 明細 PK→entity 索引 (O(n²) 回避)
        Map<DetailKey, TPurchaseDetail> detailByKey = tPurchaseDetailList.stream()
                .collect(Collectors.toMap(
                        d -> new DetailKey(d.getShopNo(), d.getExtPurchaseNo(), d.getPurchaseDetailNo()),
                        d -> d,
                        (a, b) -> a));
        int changedDetailCount = 0;
        boolean isPurchaseChecked = false;
        for (WSmilePurchaseOutputFile modifiedPurchaseFile : modifiedPurchaseFileList) {
            DetailKey key = new DetailKey(
                    modifiedPurchaseFile.getShopNo(),
                    modifiedPurchaseFile.getShoriRenban(),
                    modifiedPurchaseFile.getGyou());
            TPurchaseDetail existTPurchaseDetail = detailByKey.get(key);

            if (existTPurchaseDetail == null) {
                log.error(String.format("仕入更新処理で仕入明細が見つかりません。処理連番:%d 明細番号:%d", modifiedPurchaseFile.getShoriRenban(), modifiedPurchaseFile.getGyou()));
                continue;
            }

            boolean detailChanged = false;
            if (!Objects.equals(existTPurchaseDetail.getShopNo(), modifiedPurchaseFile.getShopNo())) {
                log.debug("ショップ番号が変更されました。shop_no:{} 処理連番:{} 行:{} 変更後shop_no：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), modifiedPurchaseFile.getShopNo());
                existTPurchaseDetail.setShopNo(modifiedPurchaseFile.getShopNo());
                detailChanged = true;
            }
            if (!BigDecimalUtil.isEqual(existTPurchaseDetail.getGoodsNum(), modifiedPurchaseFile.getSuuryou())) {
                log.debug("仕入数量が変更されました。shop_no:{} 処理連番:{} 行:{} 商品コード:{} 旧数量:{} 変更後数量：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getGoodsCode(), existTPurchaseDetail.getGoodsNum(), modifiedPurchaseFile.getSuuryou());
                existTPurchaseDetail.setGoodsNum(modifiedPurchaseFile.getSuuryou());
                detailChanged = true;
            }

            if (!StringUtil.isEqual(existTPurchaseDetail.getGoodsCode(), modifiedPurchaseFile.getShouhinCode())) {
                log.debug("商品コードが変更されました。shop_no:{} 処理連番:{} 行:{} 旧商品コード:{} 変更後商品コード：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getGoodsCode(), modifiedPurchaseFile.getShouhinCode());
                existTPurchaseDetail.setGoodsCode(modifiedPurchaseFile.getShouhinCode());
                detailChanged = true;
            }
            if (!StringUtil.isEqual(existTPurchaseDetail.getGoodsName(), modifiedPurchaseFile.getShouhinMei())) {
                log.debug("商品名が変更されました。shop_no:{} 処理連番:{} 行:{} 旧商品名:{} 変更後商品名：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getGoodsName(), modifiedPurchaseFile.getShouhinMei());
                existTPurchaseDetail.setGoodsName(modifiedPurchaseFile.getShouhinMei());
                detailChanged = true;
            }
            if (!BigDecimalUtil.isEqual(existTPurchaseDetail.getGoodsPrice(), modifiedPurchaseFile.getTanka())) {
                log.debug("商品単価が変更されました。shop_no:{} 処理連番:{} 行:{} 旧単価:{} 変更後単価：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getGoodsPrice(), modifiedPurchaseFile.getTanka());
                existTPurchaseDetail.setGoodsPrice(modifiedPurchaseFile.getTanka());
                detailChanged = true;
            }
            BigDecimal newPurchaseTaxRate = BigDecimalUtil.requireTaxRate(modifiedPurchaseFile.getShouhizeiritsu(),
                    String.format("shori_renban=%d, gyou=%d, shouhin_code=%s", modifiedPurchaseFile.getShoriRenban(), modifiedPurchaseFile.getGyou(), modifiedPurchaseFile.getShouhinCode()));
            if (!BigDecimalUtil.isEqual(existTPurchaseDetail.getTaxRate(), newPurchaseTaxRate)) {
                log.debug("消費税率が変更されました。shop_no:{} 処理連番:{} 行:{} 旧消費税率:{} 変更後消費税率：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getTaxRate(), newPurchaseTaxRate);
                existTPurchaseDetail.setTaxRate(newPurchaseTaxRate);
                detailChanged = true;
            }
            if (!StringUtil.isEqual(existTPurchaseDetail.getTaxType(), modifiedPurchaseFile.getKazeiKubun())) {
                log.debug("課税区分が変更されました。shop_no:{} 処理連番:{} 行:{} 旧課税区分:{} 変更後課税区分：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getTaxType(), modifiedPurchaseFile.getKazeiKubun());
                existTPurchaseDetail.setTaxType(modifiedPurchaseFile.getKazeiKubun());
                detailChanged = true;
            }
            BigDecimal subTotal = modifiedPurchaseFile.getKingaku();
            if (!BigDecimalUtil.isNullZero(modifiedPurchaseFile.getSuuryou()) && !BigDecimalUtil.isNullZero(modifiedPurchaseFile.getTanka())) {
                // 数量と単価がそれぞれ0でなければ、税抜合計は数量×単価とする
                BigDecimal suuryouTanka = modifiedPurchaseFile.getSuuryou().multiply(modifiedPurchaseFile.getTanka()).setScale(0, RoundingMode.HALF_UP);
                // SMILE の税抜小計 (kingaku) と「数量 × 単価」の乖離を検出する。
                // 値引伝票では単価が異なる場合があるため、diff は kingaku(subTotal) と比較する。
                BigDecimal diff = subTotal == null ? suuryouTanka : suuryouTanka.subtract(subTotal);
                if (diff.abs().compareTo(BigDecimal.ONE) > 0) {
                    log.info("税抜小計の差が2円以上あります。shop_no:{} 処理連番:{} 行:{} SMILE税抜小計:{} 数量×単価税抜小計：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), subTotal, suuryouTanka);
                }
            }
            if (!BigDecimalUtil.isEqual(existTPurchaseDetail.getSubtotal(), subTotal)) {
                log.debug("税抜小計が変更されました。shop_no:{} 処理連番:{} 行:{} 旧税抜小計:{} 変更後税抜小計：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getSubtotal(), subTotal);
                existTPurchaseDetail.setSubtotal(subTotal);
                detailChanged = true;
            }
            if (!isPurchaseChecked) {
                if (!StringUtil.isEqual(existPurchase.getPurchaseCode(), modifiedPurchaseFile.getShiiresakiCode()) && Objects.equals(existPurchase.getShopNo(), modifiedPurchaseFile.getShopNo())) {
                    log.debug("得意先コード変更　shop_no:{} 処理連番:{} 旧得意先コード:{} 変更後得意先コード:{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), existPurchase.getPurchaseCode(), modifiedPurchaseFile.getShiiresakiCode());
                    existPurchase.setPurchaseCode(modifiedPurchaseFile.getShiiresakiCode());
                }
                if (!existPurchase.getPurchaseDate().isEqual(modifiedPurchaseFile.getDenpyouHizuke())) {
                    log.debug("仕入日付が変更されました。shop_no:{} 処理連番:{} 旧仕入日付:{} 変更後仕入日付：{}", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), existPurchase.getPurchaseDate(), modifiedPurchaseFile.getDenpyouHizuke());
                    existPurchase.setPurchaseDate(modifiedPurchaseFile.getDenpyouHizuke());
                }
                isPurchaseChecked = true;
            }
            if (detailChanged) {
                this.tPurchaseDetailService.update(existTPurchaseDetail);
                changedDetailCount++;
            }
        }
        // 変更対象外の明細を含む全明細で合計を再計算する（updatedTPurchaseDetailList だけに差し替えると
        // 合計に部分集合しか載らない）。getPurchaseDetailList() はエンティティ内参照の共有で in-place 更新されている。
        calculateTotalAmount(existPurchase);
        this.tPurchaseService.update(existPurchase);
        log.debug("仕入更新完了 shop_no:{} 処理連番:{} 変更明細数:{}/{}", shopNo, shorirenban, changedDetailCount, modifiedPurchaseFileList.size());
    }

    /**
     * 仕入明細を一意に特定する複合キー。{@link TPurchaseDetail} の PK は
     * (shop_no, ext_purchase_no = shori_renban, purchase_detail_no = gyou) の 3 つ。
     */
    private record DetailKey(Integer shopNo, Long extPurchaseNo, Integer purchaseDetailNo) {}
}
