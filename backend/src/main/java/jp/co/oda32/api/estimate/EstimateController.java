package jp.co.oda32.api.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.service.estimate.TEstimateService;
import jp.co.oda32.dto.estimate.EstimateResponse;
import jp.co.oda32.dto.estimate.EstimateStatusUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/estimates")
@RequiredArgsConstructor
public class EstimateController {

    private final TEstimateService tEstimateService;

    @GetMapping
    public ResponseEntity<List<EstimateResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer estimateNo,
            @RequestParam(required = false) Integer partnerNo,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) List<String> estimateStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate estimateDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate estimateDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate priceChangeDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate priceChangeDateTo,
            @RequestParam(required = false) BigDecimal profitRate) {
        List<TEstimate> estimates = tEstimateService.find(
                shopNo, estimateNo, partnerNo, partnerName,
                goodsName, goodsCode, estimateStatus,
                estimateDateFrom, estimateDateTo,
                priceChangeDateFrom, priceChangeDateTo,
                profitRate, Flag.NO);
        return ResponseEntity.ok(estimates.stream().map(EstimateResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/{estimateNo}")
    public ResponseEntity<EstimateResponse> get(@PathVariable Integer estimateNo) {
        TEstimate estimate = tEstimateService.getByEstimateNo(estimateNo);
        if (estimate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(EstimateResponse.fromWithDetails(estimate));
    }

    @PutMapping("/{estimateNo}/status")
    public ResponseEntity<EstimateResponse> updateStatus(
            @PathVariable Integer estimateNo,
            @Valid @RequestBody EstimateStatusUpdateRequest request) throws Exception {
        TEstimate estimate = tEstimateService.getByEstimateNo(estimateNo);
        if (estimate == null) {
            return ResponseEntity.notFound().build();
        }
        estimate.setEstimateStatus(request.getEstimateStatus());
        TEstimate saved = tEstimateService.update(estimate);
        return ResponseEntity.ok(EstimateResponse.from(saved));
    }
}
