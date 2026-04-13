package jp.co.oda32.api.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.service.estimate.EstimateCompareService;
import jp.co.oda32.domain.service.estimate.EstimateCreateService;
import jp.co.oda32.domain.service.estimate.EstimatePdfService;
import jp.co.oda32.domain.service.estimate.EstimateGoodsSearchService;
import jp.co.oda32.domain.service.estimate.TEstimateService;
import jp.co.oda32.domain.service.login.LoginUserService;
import jp.co.oda32.dto.estimate.CompareGoodsResponse;
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
    private final EstimatePdfService estimatePdfService;
    private final EstimateGoodsSearchService estimateGoodsSearchService;
    private final EstimateCompareService estimateCompareService;
    private final LoginUserService loginUserService;

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

    /**
     * shopNo アクセス制御: ログインユーザの shopNo と一致するか、admin (shopNo=0) ならOK。
     * 他店舗のリソースへのアクセスを禁じる。
     * @return null: 認可OK / 非null: エラーレスポンス
     */
    private ResponseEntity<?> checkShopAccess(Integer targetShopNo) {
        if (targetShopNo == null) {
            return null; // 対象なしは呼出側で 404 等を返す
        }
        try {
            Integer userShopNo = loginUserService.getLoginUser().getShopNo();
            if (userShopNo == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            // admin (shopNo=0) は全店舗アクセス可
            if (userShopNo != 0 && !userShopNo.equals(targetShopNo)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return null;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/{estimateNo}")
    public ResponseEntity<EstimateResponse> get(@PathVariable Integer estimateNo) {
        TEstimate estimate = tEstimateService.getByEstimateNoWithDetails(estimateNo);
        if (estimate == null) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<?> denied = checkShopAccess(estimate.getShopNo());
        if (denied != null) {
            return ResponseEntity.status(denied.getStatusCode()).build();
        }
        EstimateResponse resp = EstimateResponse.fromWithDetails(estimate);
        estimateGoodsSearchService.enrichDetailsWithPricePlanInfo(
                resp.getShopNo(), resp.getPartnerNo(), resp.getDestinationNo(), resp.getDetails());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{estimateNo}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Integer estimateNo,
            @RequestParam(required = false) String userName) throws Exception {
        TEstimate estimate = tEstimateService.getByEstimateNoWithDetails(estimateNo);
        if (estimate == null) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<?> denied = checkShopAccess(estimate.getShopNo());
        if (denied != null) {
            return ResponseEntity.status(denied.getStatusCode()).build();
        }
        byte[] pdf = estimatePdfService.generatePdf(estimate, userName);
        EstimateResponse resp = EstimateResponse.from(estimate);
        String displayName = resp.getPartnerName() != null ? resp.getPartnerName() : String.valueOf(estimateNo);
        String fileName = String.format("見積書_%s_%s.pdf",
                displayName,
                estimate.getPriceChangeDate() != null ? estimate.getPriceChangeDate().toString() : "");
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"))
                .body(pdf);
    }

    @PutMapping("/{estimateNo}/status")
    public ResponseEntity<EstimateResponse> updateStatus(
            @PathVariable Integer estimateNo,
            @Valid @RequestBody EstimateStatusUpdateRequest request) throws Exception {
        TEstimate estimate = tEstimateService.getByEstimateNo(estimateNo);
        if (estimate == null) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<?> denied = checkShopAccess(estimate.getShopNo());
        if (denied != null) {
            return ResponseEntity.status(denied.getStatusCode()).build();
        }
        estimate.setEstimateStatus(request.getEstimateStatus());
        TEstimate saved = tEstimateService.update(estimate);
        return ResponseEntity.ok(EstimateResponse.from(saved));
    }

    @PostMapping
    public ResponseEntity<EstimateResponse> create(
            @Valid @RequestBody EstimateCreateRequest request) throws Exception {
        ResponseEntity<?> denied = checkShopAccess(request.getShopNo());
        if (denied != null) {
            return ResponseEntity.status(denied.getStatusCode()).build();
        }
        TEstimate estimate = estimateCreateService.createEstimate(request);
        EstimateResponse resp = EstimateResponse.fromWithDetails(estimate);
        estimateGoodsSearchService.enrichDetailsWithPricePlanInfo(
                resp.getShopNo(), resp.getPartnerNo(), resp.getDestinationNo(), resp.getDetails());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PutMapping("/{estimateNo}")
    public ResponseEntity<EstimateResponse> update(
            @PathVariable Integer estimateNo,
            @Valid @RequestBody EstimateCreateRequest request) throws Exception {
        TEstimate existing = tEstimateService.getByEstimateNo(estimateNo);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<?> denied = checkShopAccess(existing.getShopNo());
        if (denied != null) {
            return ResponseEntity.status(denied.getStatusCode()).build();
        }
        TEstimate estimate = estimateCreateService.updateEstimate(estimateNo, request);
        EstimateResponse resp = EstimateResponse.fromWithDetails(estimate);
        estimateGoodsSearchService.enrichDetailsWithPricePlanInfo(
                resp.getShopNo(), resp.getPartnerNo(), resp.getDestinationNo(), resp.getDetails());
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{estimateNo}")
    public ResponseEntity<Void> delete(@PathVariable Integer estimateNo) throws Exception {
        TEstimate existing = tEstimateService.getByEstimateNo(estimateNo);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<?> denied = checkShopAccess(existing.getShopNo());
        if (denied != null) {
            return ResponseEntity.status(denied.getStatusCode()).build();
        }
        estimateCreateService.deleteEstimate(estimateNo);
        return ResponseEntity.noContent().build();
    }

    /**
     * 複数商品の比較用データを取得します（見積比較画面用）。
     * 仕入価格・仕入先名を含むため、admin（shopNo=0）または自店舗のデータのみアクセス可能。
     */
    @GetMapping("/compare-goods")
    public ResponseEntity<List<CompareGoodsResponse>> compareGoods(
            @RequestParam Integer shopNo,
            @RequestParam List<Integer> goodsNoList,
            @RequestParam(required = false) Integer partnerNo,
            @RequestParam(required = false) Integer destinationNo) {
        if (goodsNoList == null || goodsNoList.isEmpty() || goodsNoList.size() > COMPARE_GOODS_MAX_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        // shopNo アクセス制御
        ResponseEntity<?> denied = checkShopAccess(shopNo);
        if (denied != null) {
            return ResponseEntity.status(denied.getStatusCode()).build();
        }
        // null 要素を除外
        List<Integer> sanitized = goodsNoList.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (sanitized.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<CompareGoodsResponse> response = estimateCompareService.compareGoods(
                shopNo, sanitized, partnerNo, destinationNo);
        return ResponseEntity.ok(response);
    }

    /** compareGoods の goodsNoList 最大件数 */
    private static final int COMPARE_GOODS_MAX_SIZE = 50;

    /**
     * 仕入価格変更予定 + 見積取込明細の商品を商品名で検索します（ポップアップ検索用）。
     * 販売商品マスタに存在しないメーカー見積商品を検索できます。
     * paymentSupplierNo / makerNo を指定すると絞り込みます。
     * paymentSupplierNo は m_payment_supplier の PK で、紐づく全 m_supplier をグループとして検索します。
     */
    @GetMapping("/price-plan-goods")
    public ResponseEntity<List<EstimateGoodsSearchResponse>> searchPricePlanGoods(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) Integer paymentSupplierNo,
            @RequestParam(required = false) Integer makerNo) {
        List<EstimateGoodsSearchResponse> response = estimateGoodsSearchService.searchPricePlanGoods(
                shopNo, goodsName, paymentSupplierNo, makerNo);
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
