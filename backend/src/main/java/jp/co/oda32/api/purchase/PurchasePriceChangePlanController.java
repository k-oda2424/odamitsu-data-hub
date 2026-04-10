package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.repository.purchase.MPurchasePriceChangePlanRepository;
import jp.co.oda32.domain.service.purchase.MPurchasePriceChangePlanService;
import jp.co.oda32.dto.purchase.PurchasePriceChangePlanBulkRequest;
import jp.co.oda32.dto.purchase.PurchasePriceChangePlanCreateRequest;
import jp.co.oda32.dto.purchase.PurchasePriceChangePlanResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/purchase-price-changes")
@RequiredArgsConstructor
@Validated
public class PurchasePriceChangePlanController {

    private final MPurchasePriceChangePlanService changePlanService;
    private final MPurchasePriceChangePlanRepository changePlanRepository;

    @GetMapping
    public ResponseEntity<List<PurchasePriceChangePlanResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String janCode,
            @RequestParam(required = false) String changeReason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate changePlanDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate changePlanDateTo,
            @RequestParam(required = false) @Pattern(regexp = "standard|partner|all", message = "scopeはstandard/partner/allのいずれかです") String scope) {
        List<MPurchasePriceChangePlan> plans = changePlanService.find(
                shopNo, supplierCode, goodsCode, janCode, changeReason,
                changePlanDateFrom, changePlanDateTo, Flag.NO);
        // scope filter: standard / partner / all (default: all)
        if ("standard".equals(scope)) {
            plans = plans.stream()
                    .filter(p -> (p.getPartnerNo() == null || p.getPartnerNo() == 0)
                            && (p.getDestinationNo() == null || p.getDestinationNo() == 0))
                    .collect(Collectors.toList());
        } else if ("partner".equals(scope)) {
            plans = plans.stream()
                    .filter(p -> (p.getPartnerNo() != null && p.getPartnerNo() != 0)
                            || (p.getDestinationNo() != null && p.getDestinationNo() != 0))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(plans.stream()
                .map(PurchasePriceChangePlanResponse::from)
                .collect(Collectors.toList()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<PurchasePriceChangePlanResponse> create(
            @Valid @RequestBody PurchasePriceChangePlanCreateRequest request) throws Exception {
        MPurchasePriceChangePlan plan = MPurchasePriceChangePlan.builder()
                .shopNo(request.getShopNo())
                .goodsCode(request.getGoodsCode())
                .goodsName(request.getGoodsName())
                .janCode(request.getJanCode())
                .supplierCode(request.getSupplierCode())
                .beforePrice(request.getBeforePrice())
                .afterPrice(request.getAfterPrice())
                .changePlanDate(request.getChangePlanDate())
                .changeReason(request.getChangeReason())
                .changeContainNum(request.getChangeContainNum())
                .partnerNo(request.getPartnerNo() != null ? request.getPartnerNo() : 0)
                .destinationNo(request.getDestinationNo() != null ? request.getDestinationNo() : 0)
                .partnerPriceChangePlanCreated(false)
                .purchasePriceReflect(false)
                .build();
        MPurchasePriceChangePlan saved = changePlanService.insert(plan);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PurchasePriceChangePlanResponse.from(saved));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/bulk")
    public ResponseEntity<List<PurchasePriceChangePlanResponse>> bulkCreate(
            @Valid @RequestBody PurchasePriceChangePlanBulkRequest request) throws Exception {
        List<MPurchasePriceChangePlan> plans = request.getDetails().stream()
                .map(detail -> MPurchasePriceChangePlan.builder()
                        .shopNo(request.getShopNo())
                        .goodsCode(detail.getGoodsCode())
                        .goodsName(detail.getGoodsName())
                        .supplierCode(request.getSupplierCode())
                        .beforePrice(detail.getBeforePrice())
                        .afterPrice(detail.getAfterPrice())
                        .changePlanDate(request.getChangePlanDate())
                        .changeReason(request.getChangeReason())
                        .changeContainNum(detail.getChangeContainNum())
                        .partnerNo(request.getPartnerNo() != null ? request.getPartnerNo() : 0)
                        .destinationNo(request.getDestinationNo() != null ? request.getDestinationNo() : 0)
                        .partnerPriceChangePlanCreated(false)
                        .purchasePriceReflect(false)
                        .build())
                .collect(Collectors.toList());
        List<PurchasePriceChangePlanResponse> results = changePlanService.bulkInsert(plans).stream()
                .map(PurchasePriceChangePlanResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) throws Exception {
        MPurchasePriceChangePlan plan = changePlanRepository.findById(id).orElse(null);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }
        plan.setDelFlg(Flag.YES.getValue());
        changePlanService.update(plan);
        return ResponseEntity.noContent().build();
    }
}
