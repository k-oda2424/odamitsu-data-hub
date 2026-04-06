package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.SendOrderDetailStatus;
import jp.co.oda32.domain.model.embeddable.TSendOrderDetailPK;
import jp.co.oda32.domain.model.purchase.TSendOrder;
import jp.co.oda32.domain.model.purchase.TSendOrderDetail;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.domain.service.purchase.TSendOrderDetailService;
import jp.co.oda32.domain.service.purchase.TSendOrderService;
import jp.co.oda32.dto.purchase.SendOrderCreateRequest;
import jp.co.oda32.dto.purchase.SendOrderDetailResponse;
import jp.co.oda32.dto.purchase.SendOrderDetailStatusUpdateRequest;
import jp.co.oda32.dto.purchase.SendOrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/send-orders")
@RequiredArgsConstructor
@Log4j2
public class SendOrderController {

    private final TSendOrderService tSendOrderService;
    private final TSendOrderDetailService tSendOrderDetailService;
    private final MShopService mShopService;

    @GetMapping("/details")
    public ResponseEntity<List<SendOrderDetailResponse>> listDetails(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) Integer warehouseNo,
            @RequestParam(required = false) Integer supplierNo,
            @RequestParam(required = false) String sendOrderDetailStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sendOrderDateTimeFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sendOrderDateTimeTo) {
        List<TSendOrderDetail> details = tSendOrderDetailService.find(
                shopNo, warehouseNo, supplierNo, sendOrderDetailStatus,
                sendOrderDateTimeFrom, sendOrderDateTimeTo, Flag.NO);
        List<SendOrderDetailResponse> response = details.stream()
                .map(SendOrderDetailResponse::from)
                .sorted(Comparator.comparing(
                        SendOrderDetailResponse::getSendOrderDateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sendOrderNo}")
    public ResponseEntity<SendOrderResponse> get(@PathVariable Integer sendOrderNo) {
        TSendOrder order = tSendOrderService.getByPK(sendOrderNo);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(SendOrderResponse.from(order));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<SendOrderResponse> create(@Valid @RequestBody SendOrderCreateRequest request) throws Exception {
        // ショップの会社番号を取得
        MShop shop = mShopService.getByShopNo(request.getShopNo());
        if (shop == null) {
            return ResponseEntity.badRequest().build();
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
                caseNum = new BigDecimal(d.getSendOrderNum()).divide(new BigDecimal(d.getContainNum()), 2, java.math.RoundingMode.HALF_UP);
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
        return ResponseEntity.ok(SendOrderResponse.from(result));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{sendOrderNo}/details/{sendOrderDetailNo}/status")
    public ResponseEntity<?> updateDetailStatus(
            @PathVariable Integer sendOrderNo,
            @PathVariable Integer sendOrderDetailNo,
            @Valid @RequestBody SendOrderDetailStatusUpdateRequest request) throws Exception {
        TSendOrderDetail detail = tSendOrderDetailService.getByPK(
                TSendOrderDetailPK.builder()
                        .sendOrderNo(sendOrderNo)
                        .sendOrderDetailNo(sendOrderDetailNo)
                        .build());
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }

        // ステータス後退チェック
        String currentStatus = detail.getSendOrderDetailStatus();
        String newStatus = request.getSendOrderDetailStatus();
        if (currentStatus.compareTo(newStatus) >= 0) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "ステータスを後退させることはできません"));
        }

        // ステータス別バリデーション
        SendOrderDetailStatus status = SendOrderDetailStatus.purse(newStatus);
        if (status == null) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "無効なステータスです"));
        }
        if (status == SendOrderDetailStatus.ARRIVAL_TO_PROMISE && request.getArrivePlanDate() == null) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "入荷予定日は必須です"));
        }
        if (status == SendOrderDetailStatus.ARRIVED && (request.getArrivedNum() == null || request.getArrivedNum().compareTo(BigDecimal.ZERO) <= 0)) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "入荷数量は必須です"));
        }

        // 更新
        detail.setSendOrderDetailStatus(newStatus);
        if (request.getArrivePlanDate() != null) {
            detail.setArrivePlanDate(request.getArrivePlanDate());
        }
        if (request.getArrivedDate() != null) {
            detail.setArrivedDate(request.getArrivedDate());
        }
        if (request.getArrivedNum() != null) {
            detail.setArrivedNum(request.getArrivedNum());
            detail.setDifferenceNum(request.getArrivedNum().subtract(new BigDecimal(detail.getSendOrderNum())));
        }

        tSendOrderDetailService.update(detail);
        log.info("発注明細ステータス更新 sendOrderNo:{}, detailNo:{}, status:{}", sendOrderNo, sendOrderDetailNo, newStatus);
        return ResponseEntity.ok(java.util.Map.of("message", "更新しました"));
    }
}
