package jp.co.oda32.api.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.service.estimate.EstimateCreateService;
import jp.co.oda32.domain.service.estimate.EstimateGoodsSearchService;
import jp.co.oda32.domain.service.estimate.TEstimateService;
import jp.co.oda32.dto.estimate.EstimateCreateRequest;
import jp.co.oda32.dto.estimate.EstimateGoodsSearchResponse;
import jp.co.oda32.dto.estimate.EstimateResponse;
import jp.co.oda32.dto.estimate.EstimateStatusUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/estimates")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class EstimateController {

    private final TEstimateService tEstimateService;
    private final EstimateCreateService estimateCreateService;
    private final EstimateGoodsSearchService estimateGoodsSearchService;

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

    @PreAuthorize("isAuthenticated()")
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

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<EstimateResponse> create(
            @Valid @RequestBody EstimateCreateRequest request) throws Exception {
        TEstimate estimate = estimateCreateService.createEstimate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EstimateResponse.fromWithDetails(estimate));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{estimateNo}")
    public ResponseEntity<EstimateResponse> update(
            @PathVariable Integer estimateNo,
            @Valid @RequestBody EstimateCreateRequest request) throws Exception {
        TEstimate estimate = estimateCreateService.updateEstimate(estimateNo, request);
        return ResponseEntity.ok(EstimateResponse.fromWithDetails(estimate));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{estimateNo}")
    public ResponseEntity<Void> delete(@PathVariable Integer estimateNo) throws Exception {
        estimateCreateService.deleteEstimate(estimateNo);
        return ResponseEntity.noContent().build();
    }

    /**
     * 仕入価格変更予定 + 見積取込明細の商品を商品名で検索します（ポップアップ検索用）。
     * 販売商品マスタに存在しないメーカー見積商品を検索できます。
     */
    @GetMapping("/price-plan-goods")
    public ResponseEntity<List<EstimateGoodsSearchResponse>> searchPricePlanGoods(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String goodsName) {
        List<EstimateGoodsSearchResponse> response = estimateGoodsSearchService.searchPricePlanGoods(shopNo, goodsName);
        return ResponseEntity.ok(response);
    }

    /**
     * 商品コードまたはJANコードで商品情報を取得します（明細行の入力時に使用）。
     * 商品コード入力 → blur/Enter で呼び出されます。
     *
     * 入力値の文字数で分岐:
     * - 8文字以下: 商品コードとして検索（販売商品マスタ → ワーク）
     * - 9文字以上: JANコードとして検索（商品マスタ → 仕入価格変更予定）
     */
    @GetMapping("/goods-search")
    public ResponseEntity<EstimateGoodsSearchResponse> searchGoods(
            @RequestParam Integer shopNo,
            @RequestParam String code,
            @RequestParam(required = false) Integer partnerNo,
            @RequestParam(required = false) Integer destinationNo) {
        Optional<EstimateGoodsSearchResponse> result = estimateGoodsSearchService.searchGoods(
                shopNo, code, partnerNo, destinationNo);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
