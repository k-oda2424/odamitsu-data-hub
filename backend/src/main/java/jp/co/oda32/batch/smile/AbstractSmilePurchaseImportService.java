package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.domain.service.purchase.TPurchaseDetailService;
import jp.co.oda32.domain.service.purchase.TPurchaseService;
import jp.co.oda32.domain.service.smile.WSmilePurchaseOutputFileService;
import jp.co.oda32.util.PurchaseUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Smile仕入明細ワークテーブルを使用して
 * 本システムに注文データを登録、更新するタスクレットクラス
 *
 * @author k_oda
 * @since 2024/09/11
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public abstract class AbstractSmilePurchaseImportService {
    @Autowired
    protected WSmilePurchaseOutputFileService wSmilePurchaseOutputFileService;
    @Autowired
    protected MSupplierService mSupplierService;
    @Autowired
    protected TPurchaseService tPurchaseService;
    @Autowired
    protected WSalesGoodsService wSalesGoodsService;
    @Autowired
    protected TPurchaseDetailService tPurchaseDetailService;

    protected Map<SalesGoodsKey, WSalesGoods> wSalesGoodsMap = new ConcurrentHashMap<>();
    protected Map<SupplierKey, MSupplier> mSupplierMap = new ConcurrentHashMap<>();

    protected void calculateTotalAmount(TPurchase tPurchase) {
        // 明細の合計の算出
        List<TPurchaseDetail> tPurchasePurchaseDetailList = tPurchase.getPurchaseDetailList();
        // PurchaseUtilクラスを使用して仕入の合計金額と消費税額を再計算
        PurchaseUtil.recalculateTPurchase(tPurchase, tPurchasePurchaseDetailList);
    }

    public void preProcess(List<WSmilePurchaseOutputFile> newPurchaseList) {
        // ページごとに preProcess が呼ばれるため、前回の map を必ずクリアする（メモリ肥大・古いキャッシュヒット防止）
        wSalesGoodsMap.clear();
        mSupplierMap.clear();

        // shop_no,shouhin_codeのListのMapを作成 TODO 商品コードが空のときどうするか
        Map<Integer, List<String>> goodsMap = newPurchaseList.stream()
                .filter(wSmilePurchaseOutputFile -> !StringUtil.isEmpty(wSmilePurchaseOutputFile.getShouhinCode()))
                .collect(Collectors.groupingBy(
                        WSmilePurchaseOutputFile::getShopNo,
                        Collectors.mapping(WSmilePurchaseOutputFile::getShouhinCode, Collectors.toList())
                ));

        for (Map.Entry<Integer, List<String>> entry : goodsMap.entrySet()) {
            List<WSalesGoods> wSalesGoodsList = this.wSalesGoodsService.findByShopNoAndGoodsCode(entry.getKey(), entry.getValue());
            wSalesGoodsList.forEach(wSalesGoods -> {
                SalesGoodsKey salesGoodsKey = new SalesGoodsKey(wSalesGoods.getShopNo(), wSalesGoods.getGoodsCode());
                wSalesGoodsMap.put(salesGoodsKey, wSalesGoods);
            });
        }

        // 全仕入先をマップに入れておく
        List<MSupplier> mSupplierList = this.mSupplierService.findAll();
        mSupplierList.forEach(mSupplier -> {
            SupplierKey supplierKey = new SupplierKey(mSupplier.getShopNo(), mSupplier.getSupplierCode());
            mSupplierMap.put(supplierKey, mSupplier);
        });
    }
}
