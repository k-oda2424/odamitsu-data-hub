package jp.co.oda32.api.bcart;

import jakarta.validation.Valid;
import jp.co.oda32.constant.BcartShipmentStatus;
import jp.co.oda32.domain.service.bcart.BCartShippingInputService;
import jp.co.oda32.dto.bcart.BCartShippingBulkStatusRequest;
import jp.co.oda32.dto.bcart.BCartShippingInputResponse;
import jp.co.oda32.dto.bcart.BCartShippingUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bcart")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BCartController {

    private final BCartShippingInputService bCartShippingInputService;

    @GetMapping("/shipping")
    public ResponseEntity<List<BCartShippingInputResponse>> listShipping(
            @RequestParam(required = false) List<BcartShipmentStatus> statuses,
            @RequestParam(required = false) String partnerCode) {
        return ResponseEntity.ok(bCartShippingInputService.search(statuses, partnerCode));
    }

    @PutMapping("/shipping")
    public ResponseEntity<Void> saveAll(
            @RequestBody @Valid List<BCartShippingUpdateRequest> requests) {
        bCartShippingInputService.saveAll(requests);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/shipping/bulk-status")
    public ResponseEntity<Void> bulkUpdateStatus(
            @RequestBody @Valid BCartShippingBulkStatusRequest request) {
        bCartShippingInputService.bulkUpdateStatus(request.bCartLogisticsIds(), request.shipmentStatus());
        return ResponseEntity.ok().build();
    }
}
