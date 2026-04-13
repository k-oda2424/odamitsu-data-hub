package jp.co.oda32.api.comparison;

import jp.co.oda32.domain.model.estimate.TEstimateComparison;
import jp.co.oda32.domain.service.estimate.EstimateComparisonCreateService;
import jp.co.oda32.domain.service.estimate.TEstimateComparisonService;
import jp.co.oda32.domain.service.login.LoginUserService;
import jp.co.oda32.dto.comparison.ComparisonCreateRequest;
import jp.co.oda32.dto.comparison.ComparisonResponse;
import jp.co.oda32.dto.comparison.ComparisonStatusUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/estimate-comparisons")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class EstimateComparisonController {

    private final TEstimateComparisonService comparisonService;
    private final EstimateComparisonCreateService createService;
    private final LoginUserService loginUserService;

    private ResponseEntity<?> checkShopAccess(Integer targetShopNo) {
        if (targetShopNo == null) return null;
        try {
            Integer userShopNo = loginUserService.getLoginUser().getShopNo();
            if (userShopNo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            if (userShopNo != 0 && !userShopNo.equals(targetShopNo)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return null;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ComparisonResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer partnerNo,
            @RequestParam(required = false) List<String> comparisonStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate comparisonDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate comparisonDateTo,
            @RequestParam(required = false) String title) {
        // admin以外は自身のshopNoで強制フィルタ
        Integer effectiveShopNo = shopNo;
        try {
            Integer userShopNo = loginUserService.getLoginUser().getShopNo();
            if (userShopNo != null && userShopNo != 0) {
                effectiveShopNo = userShopNo;
            }
        } catch (Exception ignored) {
        }
        List<TEstimateComparison> results = comparisonService.find(
                effectiveShopNo, partnerNo, comparisonStatus, comparisonDateFrom, comparisonDateTo, title);
        return ResponseEntity.ok(results.stream().map(ComparisonResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/{comparisonNo}")
    public ResponseEntity<ComparisonResponse> get(@PathVariable Integer comparisonNo) {
        TEstimateComparison c = comparisonService.getByComparisonNo(comparisonNo);
        if (c == null) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = checkShopAccess(c.getShopNo());
        if (denied != null) return ResponseEntity.status(denied.getStatusCode()).build();
        return ResponseEntity.ok(ComparisonResponse.fromWithDetails(c));
    }

    @PostMapping
    public ResponseEntity<ComparisonResponse> create(
            @Valid @RequestBody ComparisonCreateRequest request) throws Exception {
        ResponseEntity<?> denied = checkShopAccess(request.getShopNo());
        if (denied != null) return ResponseEntity.status(denied.getStatusCode()).build();
        TEstimateComparison created = createService.createComparison(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ComparisonResponse.fromWithDetails(created));
    }

    @PutMapping("/{comparisonNo}")
    public ResponseEntity<ComparisonResponse> update(
            @PathVariable Integer comparisonNo,
            @Valid @RequestBody ComparisonCreateRequest request) throws Exception {
        TEstimateComparison existing = comparisonService.getByComparisonNo(comparisonNo);
        if (existing == null) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = checkShopAccess(existing.getShopNo());
        if (denied != null) return ResponseEntity.status(denied.getStatusCode()).build();
        // 編集可能ステータスチェック（00:作成, 20:修正のみ）
        String status = existing.getComparisonStatus();
        if (!"00".equals(status) && !"20".equals(status)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        TEstimateComparison updated = createService.updateComparison(comparisonNo, request);
        return ResponseEntity.ok(ComparisonResponse.fromWithDetails(updated));
    }

    @DeleteMapping("/{comparisonNo}")
    public ResponseEntity<Void> delete(@PathVariable Integer comparisonNo) throws Exception {
        TEstimateComparison existing = comparisonService.getByComparisonNo(comparisonNo);
        if (existing == null) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = checkShopAccess(existing.getShopNo());
        if (denied != null) return ResponseEntity.status(denied.getStatusCode()).build();
        comparisonService.softDeleteGroupsAndDetails(comparisonNo);
        comparisonService.deleteHeader(existing);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{comparisonNo}/status")
    public ResponseEntity<ComparisonResponse> updateStatus(
            @PathVariable Integer comparisonNo,
            @Valid @RequestBody ComparisonStatusUpdateRequest request) throws Exception {
        TEstimateComparison existing = comparisonService.getByComparisonNo(comparisonNo);
        if (existing == null) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = checkShopAccess(existing.getShopNo());
        if (denied != null) return ResponseEntity.status(denied.getStatusCode()).build();
        existing.setComparisonStatus(request.getComparisonStatus());
        TEstimateComparison saved = comparisonService.updateHeader(existing);
        return ResponseEntity.ok(ComparisonResponse.from(saved));
    }

    @PostMapping("/from-estimate/{estimateNo}")
    public ResponseEntity<ComparisonResponse> createFromEstimate(
            @PathVariable Integer estimateNo) throws Exception {
        // 元見積の店舗権限をチェック
        var estimate = createService.getEstimate(estimateNo);
        if (estimate == null) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = checkShopAccess(estimate.getShopNo());
        if (denied != null) return ResponseEntity.status(denied.getStatusCode()).build();
        TEstimateComparison created = createService.createFromEstimate(estimateNo);
        return ResponseEntity.status(HttpStatus.CREATED).body(ComparisonResponse.fromWithDetails(created));
    }
}
