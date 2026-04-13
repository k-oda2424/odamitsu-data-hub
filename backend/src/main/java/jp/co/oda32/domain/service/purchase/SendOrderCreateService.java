package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.constant.SendOrderDetailStatus;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.model.purchase.TSendOrder;
import jp.co.oda32.domain.model.purchase.TSendOrderDetail;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.dto.purchase.SendOrderCreateRequest;
import jp.co.oda32.dto.purchase.SendOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 発注作成サービス
 * ヘッダー・明細の構築とトランザクション管理を担当
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class SendOrderCreateService {

    private final TSendOrderService tSendOrderService;
    private final TSendOrderDetailService tSendOrderDetailService;
    private final MShopService mShopService;

    /**
     * 発注を作成します。
     *
     * @param request 発注作成リクエスト
     * @return 作成結果のレスポンス。ショップが見つからない場合は null
     */
    @Transactional
    public SendOrderResponse createSendOrder(SendOrderCreateRequest request) throws Exception {
        // ショップの会社番号を取得
        MShop shop = mShopService.getByShopNo(request.getShopNo());
        if (shop == null) {
            return null;
        }
        Integer companyNo = shop.getCompanyNo();

        // ヘッダー作成
        TSendOrder sendOrder = TSendOrder.builder()
                .sendOrderDateTime(request.getSendOrderDateTime())
                .desiredDeliveryDate(request.getDesiredDeliveryDate())
                .shopNo(request.getShopNo())
                .companyNo(companyNo)
                .supplierNo(request.getSupplierNo())
                .sendOrderStatus(SendOrderDetailStatus.SEND_ORDER.getCode())
                .warehouseNo(request.getWarehouseNo())
                .build();
        TSendOrder saved = tSendOrderService.insert(sendOrder);
        log.info("発注登録 sendOrderNo:{}, supplierNo:{}", saved.getSendOrderNo(), saved.getSupplierNo());

        // 明細作成
        List<TSendOrderDetail> detailList = new ArrayList<>();
        int detailNo = 1;
        for (SendOrderCreateRequest.SendOrderDetailCreateRequest d : request.getDetails()) {
            BigDecimal caseNum = null;
            if (d.getContainNum() != null && d.getContainNum() > 0) {
                caseNum = new BigDecimal(d.getSendOrderNum()).divide(new BigDecimal(d.getContainNum()), 2, RoundingMode.HALF_UP);
            }
            TSendOrderDetail detail = TSendOrderDetail.builder()
                    .sendOrderNo(saved.getSendOrderNo())
                    .sendOrderDetailNo(detailNo++)
                    .shopNo(request.getShopNo())
                    .companyNo(companyNo)
                    .warehouseNo(request.getWarehouseNo())
                    .goodsNo(d.getGoodsNo())
                    .goodsCode(d.getGoodsCode())
                    .goodsName(d.getGoodsName())
                    .goodsPrice(d.getGoodsPrice())
                    .sendOrderNum(d.getSendOrderNum())
                    .sendOrderCaseNum(caseNum)
                    .containNum(d.getContainNum())
                    .sendOrderDetailStatus(SendOrderDetailStatus.SEND_ORDER.getCode())
                    .build();
            detailList.add(detail);
        }
        tSendOrderDetailService.insert(detailList);

        // 登録結果を返す
        TSendOrder result = tSendOrderService.getByPK(saved.getSendOrderNo());
        return SendOrderResponse.from(result);
    }
}
