package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.TSendOrder;
import jp.co.oda32.domain.model.purchase.TSendOrderDetail;
import jp.co.oda32.domain.service.purchase.SendOrderCreateService;
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

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/send-orders")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Log4j2
public class SendOrderController {

    private final TSendOrderService tSendOrderService;
    private final TSendOrderDetailService tSendOrderDetailService;
    private final SendOrderCreateService sendOrderCreateService;

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
        SendOrderResponse response = sendOrderCreateService.createSendOrder(request);
        if (response == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{sendOrderNo}/details/{sendOrderDetailNo}/status")
    public ResponseEntity<?> updateDetailStatus(
            @PathVariable Integer sendOrderNo,
            @PathVariable Integer sendOrderDetailNo,
            @Valid @RequestBody SendOrderDetailStatusUpdateRequest request) throws Exception {
        try {
            TSendOrderDetail updated = tSendOrderDetailService.transitionStatus(sendOrderNo, sendOrderDetailNo, request);
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }
            log.info("発注明細ステータス更新 sendOrderNo:{}, detailNo:{}, status:{}",
                    sendOrderNo, sendOrderDetailNo, request.getSendOrderDetailStatus());
            return ResponseEntity.ok(java.util.Map.of("message", "更新しました"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }
}
