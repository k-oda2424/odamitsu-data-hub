package jp.co.oda32.api.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrder;
import jp.co.oda32.domain.service.bcart.BCartOrderService;
import jp.co.oda32.dto.bcart.BCartShippingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/bcart")
@RequiredArgsConstructor
public class BCartController {

    private final BCartOrderService bCartOrderService;

    @GetMapping("/shipping")
    public ResponseEntity<List<BCartShippingResponse>> listShipping(
            @RequestParam(required = false) String status) {
        List<BCartOrder> orders = bCartOrderService.findByStatus(status);
        return ResponseEntity.ok(orders.stream().map(BCartShippingResponse::from).collect(Collectors.toList()));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/shipping/{orderId}/status")
    public ResponseEntity<Void> updateShippingStatus(
            @PathVariable Long orderId,
            @RequestParam String status) throws Exception {
        BCartOrder order = bCartOrderService.findById(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        order.setStatus(status);
        bCartOrderService.save(order);
        return ResponseEntity.ok().build();
    }
}
