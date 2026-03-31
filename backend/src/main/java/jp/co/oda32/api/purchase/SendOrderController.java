package jp.co.oda32.api.purchase;

import jp.co.oda32.domain.model.purchase.TSendOrder;
import jp.co.oda32.domain.repository.purchase.TSendOrderRepository;
import jp.co.oda32.dto.purchase.SendOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/send-orders")
@RequiredArgsConstructor
public class SendOrderController {

    private final TSendOrderRepository tSendOrderRepository;

    // TODO: Service層を新設してRepository直接注入を解消する
    @GetMapping
    public ResponseEntity<List<SendOrderResponse>> list(
            @RequestParam(required = false) Integer shopNo) {
        List<TSendOrder> orders = tSendOrderRepository.findAll();
        return ResponseEntity.ok(orders.stream()
                .filter(o -> shopNo == null || shopNo.equals(o.getShopNo()))
                .map(SendOrderResponse::from)
                .collect(Collectors.toList()));
    }
}
