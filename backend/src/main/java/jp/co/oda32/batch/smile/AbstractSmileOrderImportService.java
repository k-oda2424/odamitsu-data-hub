package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.MDeliveryDestination;
import jp.co.oda32.domain.model.order.TOrder;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MCompanyService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.order.*;
import jp.co.oda32.domain.service.smile.WSmileOrderOutputFileService;
import jp.co.oda32.util.OrderUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Smile売上明細ワークテーブルを使用して
 * 本システムに注文データを登録、更新するタスクレットクラス
 *
 * @author k_oda
 * @since 2024/05/08
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public abstract class AbstractSmileOrderImportService {
    @Autowired
    protected MGoodsService mGoodsService;
    @Autowired
    protected WSmileOrderOutputFileService wSmileOrderOutputFileService;
    @Autowired
    protected MPartnerService mPartnerService;
    @Autowired
    protected MCompanyService mCompanyService;
    @Autowired
    protected TOrderService tOrderService;
    @Autowired
    protected WSalesGoodsService wSalesGoodsService;
    @Autowired
    protected MDeliveryDestinationService mDeliveryDestinationService;
    @Autowired
    protected TOrderDetailService tOrderDetailService;
    @Autowired
    protected TDeliveryService tDeliveryService;
    @Autowired
    protected TDeliveryDetailService tDeliveryDetailService;

    protected Map<SalesGoodsKey, WSalesGoods> wSalesGoodsMap = new ConcurrentHashMap<>();
    protected Map<PartnerKey, MPartner> mPartnerMap = new ConcurrentHashMap<>();

    protected void calculateTotalAmount(TOrder tOrder) {
        // 明細の合計の算出
        List<TOrderDetail> tOrderDetailList = tOrder.getOrderDetailList();
        // OrderUtilクラスを使用して注文の合計金額と消費税額を再計算
        OrderUtil.recalculateTOrder(tOrder, tOrderDetailList);
    }

    protected MDeliveryDestination getDestination(MPartner mPartner, WSmileOrderOutputFile wSmileOrderOutputFile) throws Exception {
        MDeliveryDestination deliveryDestination = null;
        try {
            deliveryDestination = this.mDeliveryDestinationService.getByUniqKey(mPartner.getCompanyNo(), wSmileOrderOutputFile.getNouhinSakiCode());
        } catch (Exception e) {
            log.error(String.format("納品先検索でエラーが発生しました。%s", e.getMessage()));
        }
        if (deliveryDestination == null) {
            // 届け先見つからないので新規登録
            MDeliveryDestination destination = MDeliveryDestination.builder()
                    .companyNo(mPartner.getCompanyNo())
                    .partnerNo(mPartner.getPartnerNo())
                    .shopNo(mPartner.getShopNo())
                    .destinationCode(wSmileOrderOutputFile.getNouhinSakiCode())
                    .destinationName(wSmileOrderOutputFile.getNouhinSakiMei())
                    .build();
            return this.mDeliveryDestinationService.insert(destination);
        }
        if (!StringUtil.isEqual(deliveryDestination.getDestinationName(), wSmileOrderOutputFile.getNouhinSakiMei())) {
            // 更新
            log.info(String.format("届け先マスタの届け先名を更新します。%s → %s", deliveryDestination.getDestinationName(), wSmileOrderOutputFile.getNouhinSakiMei()));
            deliveryDestination.setDestinationName(wSmileOrderOutputFile.getNouhinSakiMei());
            deliveryDestination = this.mDeliveryDestinationService.update(deliveryDestination);
        }
        return deliveryDestination;
    }

}
