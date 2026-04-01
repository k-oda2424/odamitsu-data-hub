package jp.co.oda32.api.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.order.TOrder;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.repository.order.TOrderDetailRepository;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import jp.co.oda32.domain.service.order.TOrderService;
import jp.co.oda32.domain.specification.order.TOrderDetailSpecification;
import jp.co.oda32.dto.order.OrderDetailResponse;
import jp.co.oda32.dto.order.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Log4j2
public class OrderController {

    private final TOrderService tOrderService;
    private final TOrderDetailService tOrderDetailService;
    private final TOrderDetailRepository tOrderDetailRepository;
    private final TOrderDetailSpecification spec = new TOrderDetailSpecification();

    @GetMapping
    public ResponseEntity<List<OrderResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer companyNo) {
        List<TOrder> orders = tOrderService.find(shopNo, companyNo, null, null, null, null, null, null, Flag.NO);
        return ResponseEntity.ok(orders.stream().map(OrderResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/details")
    public ResponseEntity<List<OrderDetailResponse>> listDetails(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) Integer companyNo,
            @RequestParam(required = false) Integer partnerNo,
            @RequestParam(required = false) String slipNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String orderDetailStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime orderDateTimeFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime orderDateTimeTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate slipDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate slipDateTo) {
        log.info("受注一覧検索: shopNo={}, partnerNo={}, companyNo={}, goodsName={}", shopNo, partnerNo, companyNo, goodsName);
        String[] statusArray = orderDetailStatus != null ? new String[]{orderDetailStatus} : null;
        List<TOrderDetail> details = tOrderDetailRepository.findAll(
                Specification.where(spec.shopNoContains(shopNo))
                        .and(spec.companyNoContains(companyNo))
                        .and(spec.partnerNoContains(partnerNo))
                        .and(spec.slipNoContains(slipNo))
                        .and(spec.orderDetailStatusListContains(statusArray))
                        .and(spec.goodsCodeContains(goodsCode))
                        .and(spec.goodsNameContains(goodsName))
                        .and(spec.orderDateTimeContains(orderDateTimeFrom, orderDateTimeTo))
                        .and(spec.slipDateContains(slipDateFrom, slipDateTo))
                        .and(spec.delFlgContains(Flag.NO)));
        List<OrderDetailResponse> response = details.stream()
                .map(OrderDetailResponse::from)
                .sorted(Comparator.comparing(
                        OrderDetailResponse::getOrderDateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderResponse> get(@PathVariable Integer orderNo) {
        TOrder order = tOrderService.getByPK(orderNo);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
