package jp.co.oda32.batch.smile;

import jp.co.oda32.batch.goods.logic.SalesGoodsCreateService;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.TaxCategory;
import jp.co.oda32.constant.TaxType;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
import jp.co.oda32.util.BigDecimalUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SMILEからの新規受注データを処理するクラス
 * トランザクションをかけるメソッドはこのクラスに記載する
 *
 * @author k_oda
 * @since 2024/09/11
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class SmilePurchaseImportService extends AbstractSmilePurchaseImportService {
    @Autowired
    SalesGoodsCreateService salesGoodsCreateService;

    public Page<WSmilePurchaseOutputFile> findNewPurchases(Pageable firstPageable) {
        return wSmilePurchaseOutputFileService.findNewPurchases(firstPageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void newPurchaseRegister(int shopNo, long processingSerialNumber, List<WSmilePurchaseOutputFile> newPurchaseFileDataList) throws Exception {
        this.purchaseProcess(shopNo, processingSerialNumber, newPurchaseFileDataList);
    }

    public void purchaseProcess(int shopNo, long processingSerialNumber, List<WSmilePurchaseOutputFile> newPurchaseFileList) throws Exception {
        TPurchase insertTPurchase = null;
        List<TPurchaseDetail> tPurchaseDetailList = new ArrayList<>();
        newPurchaseFileList.sort(Comparator.comparing(WSmilePurchaseOutputFile::getGyou));
        for (WSmilePurchaseOutputFile newPurchaseFile : newPurchaseFileList) {
            log.info(String.format("新規仕入処理 shop_no:%d 伝票日付:%s 処理連番:%d 行番号:%d 仕入先名:%s 商品コード:%s 商品名:%s ", newPurchaseFile.getShopNo(), newPurchaseFile.getDenpyouHizuke(), newPurchaseFile.getShoriRenban(), newPurchaseFile.getGyou(), newPurchaseFile.getShiiresakiMei1(), newPurchaseFile.getShouhinCode(), newPurchaseFile.getShouhinMei()));
            List<WSmilePurchaseOutputFile> list = this.wSmilePurchaseOutputFileService.findByShopNoAndShoriRenban(newPurchaseFile.getShopNo(), newPurchaseFile.getShoriRenban());
            if (!list.isEmpty()) {
                list = list.stream().filter(wSmilePurchaseOutputFile -> Objects.equals(wSmilePurchaseOutputFile.getGyou(), newPurchaseFile.getGyou())).collect(Collectors.toList());
                if (list.isEmpty()) {
                    log.warn(String.format("shopNo:%d shoriRenban:%d gyou:%d は存在しません。", newPurchaseFile.getShopNo(), newPurchaseFile.getShoriRenban(), newPurchaseFile.getGyou()));
                    continue;
                }
            }
            if (shopNo != newPurchaseFile.getShopNo()) {
                log.warn(String.format("shopNoが異なります。処理中のshopNo:%d newPurchaseのshopNo:%d", shopNo, newPurchaseFile.getShopNo()));
            }

            // 商品名に「消費税」が含まれる場合は請求時に登録されるのでスキップする
            if (newPurchaseFile.getShouhinMei() != null && newPurchaseFile.getShouhinMei().contains("消費税")) {
                log.info(String.format("消費税関連の商品「%s」はスキップします。請求時に登録されます。", newPurchaseFile.getShouhinMei()));
                continue;
            }

            // 商品コードが空の場合は、この仕入明細をスキップ
            if (StringUtil.isEmpty(newPurchaseFile.getShouhinCode())) {
                log.warn(String.format("商品コードが空のため、商品「%s」の仕入明細処理をスキップします。", newPurchaseFile.getShouhinMei()));
                continue;
            }
            
            // 仕入先の確認
            MSupplier mSupplier = supplierProcess(newPurchaseFile);
            // 商品の確認
            WSalesGoods wSalesGoods = this.goodsProcess(newPurchaseFile);

            // 商品の登録ができなかった場合はスキップ
            if (wSalesGoods == null) {
                log.warn(String.format("商品「%s」の登録ができなかったため、この仕入明細処理をスキップします。", newPurchaseFile.getShouhinMei()));
                continue;
            }
            if (insertTPurchase == null) {
                try {
                    insertTPurchase = this.tPurchaseService.getByUniqKey(newPurchaseFile.getShopNo(), processingSerialNumber);
                } catch (Exception e) {
                    // 見つからない場合は新規登録処理
                }

                if (insertTPurchase == null) {
                    insertTPurchase = TPurchase.builder()
                            .extPurchaseNo(newPurchaseFile.getShoriRenban())
                            .shopNo(newPurchaseFile.getShopNo())
                            .companyNo(newPurchaseFile.getCompanyNo())
                            .departmentNo(Integer.valueOf(newPurchaseFile.getShiiresakiEigyoshoCode()))
                            .taxType(newPurchaseFile.getKazeiKubun())
                            .taxRate(BigDecimalUtil.requireTaxRate(newPurchaseFile.getShouhizeiritsu(),
                                    String.format("shori_renban=%d, shouhin_code=%s", newPurchaseFile.getShoriRenban(), newPurchaseFile.getShouhinCode())))
                            .purchaseDate(newPurchaseFile.getDenpyouHizuke())
                            .purchaseCode(newPurchaseFile.getDenpyouBangou().toString())
                            .note(newPurchaseFile.getBiko())
                            .supplierNo(mSupplier == null ? null : mSupplier.getSupplierNo())
                            .build();
                    insertTPurchase = this.tPurchaseService.insert(insertTPurchase);
                }
            }
            TaxCategory taxCategory;
            if (StringUtil.isEqual(TaxType.TAX_FREE.getValue(), newPurchaseFile.getKazeiKubun())) {
                // 非課税の場合消費税分類：0だが、課税区分：2になっている
                taxCategory = TaxCategory.EXEMPT;
            } else {
                // 0：通常　1：軽減税率
                taxCategory = TaxCategory.purse(newPurchaseFile.getShouhizeiBunrui());
            }
            BigDecimal subTotal = newPurchaseFile.getKingaku();
            TPurchaseDetail tPurchaseDetail = TPurchaseDetail.builder()
                    .purchaseNo(insertTPurchase.getPurchaseNo())
                    .purchaseDetailNo(newPurchaseFile.getGyou())
                    .extPurchaseNo(newPurchaseFile.getShoriRenban())
                    .shopNo(insertTPurchase.getShopNo())
                    .companyNo(insertTPurchase.getCompanyNo())
                    .goodsNo(wSalesGoods == null ? null : wSalesGoods.getGoodsNo())
                    .goodsCode(newPurchaseFile.getShouhinCode())
                    .goodsNum(newPurchaseFile.getSuuryou())
                    .goodsPrice(newPurchaseFile.getTanka())
                    .goodsName(newPurchaseFile.getShouhinMei())
                    .purchaseDate(newPurchaseFile.getDenpyouHizuke())
                    .taxRate(BigDecimalUtil.requireTaxRate(newPurchaseFile.getShouhizeiritsu(),
                            String.format("shori_renban=%d, gyou=%d, shouhin_code=%s", newPurchaseFile.getShoriRenban(), newPurchaseFile.getGyou(), newPurchaseFile.getShouhinCode())))
                    .taxType(TaxType.TAX_EXCLUDE.getValue())
                    .taxCategory(taxCategory.getCode())
                    .note(newPurchaseFile.getBiko())
                    .containNum(newPurchaseFile.getIrisu())
                    .stockProcessFlg(Flag.NO.getValue())
                    .subtotal(subTotal)
                    .build();

            tPurchaseDetail = this.tPurchaseDetailService.insert(tPurchaseDetail);
            tPurchaseDetailList.add(tPurchaseDetail);
        }

        // 処理対象の明細が1件もない場合
        if (tPurchaseDetailList.isEmpty()) {
            log.info("処理対象の明細が存在しません。");
            return;
        }

        if (insertTPurchase == null) {
            throw new Exception("t_Purchaseが生成できませんでした。");
        }

        insertTPurchase.setPurchaseDetailList(tPurchaseDetailList);
        calculateTotalAmount(insertTPurchase);
        this.tPurchaseService.update(insertTPurchase);
    }

    /**
     * 仕入先が存在するかを確認し、登録されていなければ登録する処理
     */
    public MSupplier supplierProcess(WSmilePurchaseOutputFile newSupplierContainsRecord) throws Exception {
        int shopNo = newSupplierContainsRecord.getShopNo();
        String supplierCode = newSupplierContainsRecord.getShiiresakiCode();
        if (StringUtil.isEmpty(supplierCode)) {
            return null;
        }
        SupplierKey supplierKey = new SupplierKey(shopNo, supplierCode);
        if (this.mSupplierMap.containsKey(supplierKey)) {
            return this.mSupplierMap.get(supplierKey);
        } else {
            String supplierName = String.format("%s %s", newSupplierContainsRecord.getShiiresakiMei1(), newSupplierContainsRecord.getShiiresakiMei2());
            MSupplier mSupplier = MSupplier.builder()
                    .shopNo(shopNo)
                    .supplierCode(supplierCode)
                    .supplierName(supplierName)
                    .supplierNameDisplay(newSupplierContainsRecord.getShiiresakiRyakushou())
                    .build();
            mSupplier = this.mSupplierService.insert(mSupplier);
            this.mSupplierMap.put(supplierKey, mSupplier);
            return mSupplier;
        }
    }

    private WSalesGoods goodsProcess(WSmilePurchaseOutputFile newPurchaseFile) throws Exception {
        // 商品コードが空の場合はnullを返す
        if (StringUtil.isEmpty(newPurchaseFile.getShouhinCode())) {
            log.warn(String.format("商品コードが空のため、商品「%s」の処理をスキップします。", newPurchaseFile.getShouhinMei()));
            return null;
        }
        
        SalesGoodsKey salesGoodsKey = new SalesGoodsKey(newPurchaseFile.getShopNo(), newPurchaseFile.getShouhinCode());
        WSalesGoods wSalesGoods = this.wSalesGoodsMap.get(salesGoodsKey);
        if (wSalesGoods == null) {
            log.warn(String.format("商品マスタに商品コード[%s]が存在しません。新規作成します。", newPurchaseFile.getShouhinCode()));
            wSalesGoods = this.salesGoodsCreateService.goodsProcess(newPurchaseFile);
            if (wSalesGoods != null) {
                this.wSalesGoodsMap.put(salesGoodsKey, wSalesGoods);
            }
        }
        return wSalesGoods;
    }
}
