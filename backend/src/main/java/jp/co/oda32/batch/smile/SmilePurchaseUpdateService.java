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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        List<TPurchaseDetail> updatedTPurchaseDetailList = new ArrayList<>();
        boolean isPurchaseChecked = false;
        for (WSmilePurchaseOutputFile modifiedPurchaseFile : modifiedPurchaseFileList) {
            TPurchaseDetail existTPurchaseDetail = tPurchaseDetailList.stream()
                    .filter(tPurchaseDetail -> Objects.equals(tPurchaseDetail.getShopNo(), modifiedPurchaseFile.getShopNo()))
                    .filter(tPurchaseDetail -> Objects.equals(tPurchaseDetail.getExtPurchaseNo(), modifiedPurchaseFile.getShoriRenban()))
                    .filter(tPurchaseDetail -> Objects.equals(tPurchaseDetail.getPurchaseDetailNo(), modifiedPurchaseFile.getGyou()))
                    .findFirst().orElse(null);

            if (existTPurchaseDetail == null) {
                log.error(String.format("出荷明細登録時に仕入明細が見つかりません。処理連番:%d 明細番号:%d", modifiedPurchaseFile.getShoriRenban(), modifiedPurchaseFile.getGyou()));
                continue;
            }

            if (!Objects.equals(existTPurchaseDetail.getShopNo(), modifiedPurchaseFile.getShopNo())) {
                log.info(String.format("ショップ番号が変更されました。shop_no:%d 処理連番:%s 行:%d 変更後shop_no：%d", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), modifiedPurchaseFile.getShopNo()));
                existTPurchaseDetail.setShopNo(modifiedPurchaseFile.getShopNo());
            }
            if (!Objects.equals(existTPurchaseDetail.getGoodsNum(), modifiedPurchaseFile.getSuuryou())) {
                log.info(String.format("仕入数量が変更されました。shop_no:%d 処理連番:%s 行:%d 商品コード:%s 旧数量:%s 変更後数量：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getGoodsCode(), existTPurchaseDetail.getGoodsNum(), modifiedPurchaseFile.getSuuryou()));
                existTPurchaseDetail.setGoodsNum(modifiedPurchaseFile.getSuuryou());
            }

            if (!StringUtil.isEqual(existTPurchaseDetail.getGoodsCode(), modifiedPurchaseFile.getShouhinCode())) {
                log.info(String.format("商品コードが変更されました。shop_no:%d 処理連番:%s 行:%d 旧商品コード:%s 変更後商品コード：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getGoodsCode(), modifiedPurchaseFile.getShouhinCode()));
                existTPurchaseDetail.setGoodsCode(modifiedPurchaseFile.getShouhinCode());
            }
            if (!StringUtil.isEqual(existTPurchaseDetail.getGoodsName(), modifiedPurchaseFile.getShouhinMei())) {
                log.info(String.format("商品名が変更されました。shop_no:%d 処理連番:%s 行:%d 旧商品名:%s 変更後商品名：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getGoodsName(), modifiedPurchaseFile.getShouhinMei()));
                existTPurchaseDetail.setGoodsName(modifiedPurchaseFile.getShouhinMei());
            }
            if (!BigDecimalUtil.isEqual(existTPurchaseDetail.getGoodsPrice(), modifiedPurchaseFile.getTanka())) {
                log.info(String.format("商品単価が変更されました。shop_no:%d 処理連番:%s 行:%d 旧単価:%s 変更後単価：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getGoodsPrice(), modifiedPurchaseFile.getTanka()));
                existTPurchaseDetail.setGoodsPrice(modifiedPurchaseFile.getTanka());
            }
            if (!BigDecimalUtil.isEqual(existTPurchaseDetail.getTaxRate(), modifiedPurchaseFile.getShouhizeiritsu())) {
                log.info(String.format("消費税率が変更されました。shop_no:%d 処理連番:%s 行:%d 旧消費税率:%s 変更後消費税率：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getTaxRate(), modifiedPurchaseFile.getShouhizeiritsu()));
                existTPurchaseDetail.setTaxRate(modifiedPurchaseFile.getShouhizeiritsu());
            }
            if (!StringUtil.isEqual(existTPurchaseDetail.getTaxType(), modifiedPurchaseFile.getKazeiKubun())) {
                log.info(String.format("課税区分が変更されました。shop_no:%d 処理連番:%s 行:%d 旧課税区分:%s 変更後課税区分：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getTaxType(), modifiedPurchaseFile.getKazeiKubun()));
                existTPurchaseDetail.setTaxType(modifiedPurchaseFile.getKazeiKubun());
            }
            BigDecimal subTotal = modifiedPurchaseFile.getKingaku();
            if (!BigDecimalUtil.isNullZero(modifiedPurchaseFile.getSuuryou()) && !BigDecimalUtil.isNullZero(modifiedPurchaseFile.getTanka())) {
                // 数量と単価がそれぞれ0でなければ、税抜合計は数量×単価とする
                BigDecimal suuryouTanka = modifiedPurchaseFile.getSuuryou().multiply(modifiedPurchaseFile.getTanka()).setScale(0, RoundingMode.HALF_UP);
                BigDecimal diff = suuryouTanka.subtract(modifiedPurchaseFile.getSuuryou());
                if (diff.abs().compareTo(new BigDecimal(1)) > 0) {
                    log.info(String.format("税抜小計の差が2円以上あります。shop_no:%d 処理連番:%s 行:%d SMILE税抜小計:%s 数量×単価税抜小計：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getSubtotal(), suuryouTanka));
                }
            }
            if (!BigDecimalUtil.isEqual(existTPurchaseDetail.getSubtotal(), subTotal)) {
                log.info(String.format("税抜小計が変更されました。shop_no:%d 処理連番:%s 行:%d 旧税抜小計:%s 変更後税抜小計：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), modifiedPurchaseFile.getGyou(), existTPurchaseDetail.getSubtotal(), subTotal));
                existTPurchaseDetail.setSubtotal(subTotal);
            }
            if (!isPurchaseChecked) {
                if (!StringUtil.isEqual(existPurchase.getPurchaseCode(), modifiedPurchaseFile.getShiiresakiCode()) && Objects.equals(existPurchase.getShopNo(), modifiedPurchaseFile.getShopNo())) {
                    log.info(String.format("得意先コード変更　shop_no:%d 処理連番:%s 旧得意先コード:%s 変更後得意先コード:%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), existPurchase.getPurchaseCode(), modifiedPurchaseFile.getShiiresakiCode()));
                    existPurchase.setPurchaseCode(modifiedPurchaseFile.getShiiresakiCode());
                }
                if (!existPurchase.getPurchaseDate().isEqual(modifiedPurchaseFile.getDenpyouHizuke())) {
                    log.info(String.format("仕入日付が変更されました。shop_no:%d 処理連番:%s 旧仕入日付:%s 変更後仕入日付：%s", existTPurchaseDetail.getShopNo(), existPurchase.getExtPurchaseNo(), existPurchase.getPurchaseDate(), modifiedPurchaseFile.getDenpyouHizuke()));
                    existPurchase.setPurchaseDate(modifiedPurchaseFile.getDenpyouHizuke());
                }
                isPurchaseChecked = true;
            }
            this.tPurchaseDetailService.update(existTPurchaseDetail);
            updatedTPurchaseDetailList.add(existTPurchaseDetail);
        }
        existPurchase.setPurchaseDetailList(updatedTPurchaseDetailList);
        // t_purchaseの合計金額の更新
        calculateTotalAmount(existPurchase);
        this.tPurchaseService.update(existPurchase);
    }
}
