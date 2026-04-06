package jp.co.oda32.api.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.model.estimate.IVEstimateGoods;
import jp.co.oda32.domain.model.estimate.VEstimateGoods;
import jp.co.oda32.domain.model.estimate.VEstimateGoodsSpecial;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.model.purchase.TQuoteImportDetail;
import jp.co.oda32.domain.model.purchase.TQuoteImportHeader;
import jp.co.oda32.domain.repository.purchase.TQuoteImportDetailRepository;
import jp.co.oda32.domain.repository.purchase.TQuoteImportHeaderRepository;
import jp.co.oda32.domain.service.estimate.EstimateCreateService;
import jp.co.oda32.domain.service.estimate.TEstimateService;
import jp.co.oda32.domain.service.estimate.VEstimateGoodsService;
import jp.co.oda32.domain.service.estimate.VEstimateGoodsSpecialService;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MSalesGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.purchase.MPurchasePriceChangePlanService;
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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/estimates")
@RequiredArgsConstructor
public class EstimateController {

    private final TEstimateService tEstimateService;
    private final EstimateCreateService estimateCreateService;
    private final VEstimateGoodsService vEstimateGoodsService;
    private final VEstimateGoodsSpecialService vEstimateGoodsSpecialService;
    private final MGoodsService mGoodsService;
    private final MSalesGoodsService mSalesGoodsService;
    private final WSalesGoodsService wSalesGoodsService;
    private final MPurchasePriceChangePlanService mPurchasePriceChangePlanService;
    private final TQuoteImportDetailRepository quoteImportDetailRepository;
    private final TQuoteImportHeaderRepository quoteImportHeaderRepository;

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

        List<EstimateGoodsSearchResponse> response = new ArrayList<>();

        // 1. 仕入価格変更予定（m_purchase_price_change_plan）
        List<MPurchasePriceChangePlan> plans = (goodsName != null && !goodsName.isBlank())
                ? mPurchasePriceChangePlanService.findByGoodsName(shopNo, goodsName)
                : mPurchasePriceChangePlanService.find(shopNo, null, null, null, null, null, null, Flag.NO);

        Map<String, MPurchasePriceChangePlan> uniquePlans = new LinkedHashMap<>();
        for (MPurchasePriceChangePlan plan : plans) {
            String key = (plan.getGoodsCode() != null ? plan.getGoodsCode() : plan.getJanCode())
                    + "_" + plan.getGoodsName();
            MPurchasePriceChangePlan existing = uniquePlans.get(key);
            if (existing == null || (plan.getChangePlanDate() != null
                    && (existing.getChangePlanDate() == null
                    || plan.getChangePlanDate().isAfter(existing.getChangePlanDate())))) {
                uniquePlans.put(key, plan);
            }
        }

        Set<String> addedKeys = new HashSet<>();
        for (MPurchasePriceChangePlan plan : uniquePlans.values()) {
            String displayCode = plan.getGoodsCode() != null ? plan.getGoodsCode() : plan.getJanCode();
            String pricePlanInfo = null;
            if (plan.getChangePlanDate() != null) {
                pricePlanInfo = plan.getChangePlanDate() + "より"
                        + plan.getBeforePrice() + "→" + plan.getAfterPrice();
            }
            addedKeys.add(displayCode + "_" + plan.getGoodsName());
            response.add(EstimateGoodsSearchResponse.builder()
                    .goodsCode(displayCode)
                    .goodsName(plan.getGoodsName())
                    .purchasePrice(plan.getAfterPrice())
                    .containNum(plan.getChangeContainNum())
                    .changeContainNum(plan.getChangeContainNum())
                    .nowGoodsPrice(plan.getAfterPrice())
                    .pricePlanInfo(pricePlanInfo)
                    .janCode(plan.getJanCode())
                    .source("PRICE_PLAN")
                    .purchasePriceChangePlanNo(plan.getPurchasePriceChangePlanNo())
                    .build());
        }

        // 2. 見積取込明細（t_quote_import_detail）のPENDINGデータ
        if (goodsName != null && !goodsName.isBlank()) {
            List<TQuoteImportDetail> quoteDetails = quoteImportDetailRepository
                    .findByGoodsNameContainingAndStatus(goodsName, "PENDING");
            for (TQuoteImportDetail qd : quoteDetails) {
                String displayCode = qd.getQuoteGoodsCode() != null && !qd.getQuoteGoodsCode().isBlank()
                        ? qd.getQuoteGoodsCode() : qd.getJanCode();
                String key = displayCode + "_" + qd.getQuoteGoodsName();
                if (addedKeys.contains(key)) continue;
                addedKeys.add(key);
                response.add(EstimateGoodsSearchResponse.builder()
                        .goodsCode(displayCode)
                        .goodsName(qd.getQuoteGoodsName())
                        .specification(qd.getSpecification())
                        .purchasePrice(qd.getNewPrice())
                        .containNum(qd.getQuantityPerCase() != null ? BigDecimal.valueOf(qd.getQuantityPerCase()) : null)
                        .janCode(qd.getJanCode())
                        .source("QUOTE_IMPORT")
                        .build());
            }
        }

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

        boolean isJanCode = code.length() > 8;

        Integer foundGoodsNo = null;
        String foundGoodsCode = null;

        if (!isJanCode) {
            // 商品コード検索: 8文字以下

            // 1. 販売商品マスタの商品コードで検索
            MSalesGoods masterGoods = mSalesGoodsService.getByShopNoAndGoodsCode(shopNo, code);
            if (masterGoods != null) {
                foundGoodsNo = masterGoods.getGoodsNo();
                foundGoodsCode = masterGoods.getGoodsCode();
            }

            // 2. 販売商品ワークの商品コードで検索
            if (foundGoodsNo == null) {
                WSalesGoods workGoods = wSalesGoodsService.getByShopNoAndGoodsCode(shopNo, code);
                if (workGoods != null) {
                    foundGoodsNo = workGoods.getGoodsNo();
                    foundGoodsCode = workGoods.getGoodsCode();
                }
            }
        } else {
            // JANコード検索: 9文字以上

            // 3. 商品マスタのJANコードで検索
            MGoods byJan = mGoodsService.getByJanCode(code);
            if (byJan != null) {
                foundGoodsNo = byJan.getGoodsNo();
                MSalesGoods sg = mSalesGoodsService.getByPK(shopNo, byJan.getGoodsNo());
                foundGoodsCode = sg != null ? sg.getGoodsCode() : code;
            }
        }

        // 販売商品で見つかった場合
        if (foundGoodsNo != null) {
            return buildGoodsResponse(shopNo, foundGoodsNo, foundGoodsCode, partnerNo, destinationNo);
        }

        // 4. 仕入価格変更予定で検索（商品コードまたはJANコード）
        List<MPurchasePriceChangePlan> plans;
        if (!isJanCode) {
            plans = mPurchasePriceChangePlanService.find(
                    null, null, code, null, null, null, null, Flag.NO);
        } else {
            plans = mPurchasePriceChangePlanService.find(
                    null, null, null, code, null, null, null, Flag.NO);
        }
        if (!plans.isEmpty()) {
            MPurchasePriceChangePlan plan = plans.stream()
                    .sorted(Comparator.comparing(
                            (MPurchasePriceChangePlan p) -> p.getChangePlanDate() != null ? p.getChangePlanDate() : LocalDate.MIN)
                            .reversed())
                    .findFirst().orElse(null);
            if (plan != null) {
                String displayCode = plan.getGoodsCode() != null ? plan.getGoodsCode() : plan.getJanCode();
                String pricePlanInfo = null;
                if (plan.getChangePlanDate() != null) {
                    pricePlanInfo = plan.getChangePlanDate() + "より"
                            + plan.getBeforePrice() + "→" + plan.getAfterPrice();
                }
                return ResponseEntity.ok(EstimateGoodsSearchResponse.builder()
                        .goodsCode(displayCode)
                        .goodsName(plan.getGoodsName())
                        .purchasePrice(plan.getAfterPrice())
                        .containNum(plan.getChangeContainNum())
                        .changeContainNum(plan.getChangeContainNum())
                        .nowGoodsPrice(plan.getAfterPrice())
                        .pricePlanInfo(pricePlanInfo)
                        .janCode(plan.getJanCode())
                        .source("PRICE_PLAN")
                        .purchasePriceChangePlanNo(plan.getPurchasePriceChangePlanNo())
                        .build());
            }
        }

        // 5. 見積取込明細（t_quote_import_detail）のJANコードで検索
        if (isJanCode) {
            List<TQuoteImportDetail> quoteDetails = quoteImportDetailRepository.findByJanCodeAndStatus(code, "PENDING");
            if (!quoteDetails.isEmpty()) {
                TQuoteImportDetail qd = quoteDetails.getFirst();
                String displayCode = qd.getQuoteGoodsCode() != null && !qd.getQuoteGoodsCode().isBlank()
                        ? qd.getQuoteGoodsCode() : qd.getJanCode();
                return ResponseEntity.ok(EstimateGoodsSearchResponse.builder()
                        .goodsCode(displayCode)
                        .goodsName(qd.getQuoteGoodsName())
                        .specification(qd.getSpecification())
                        .purchasePrice(qd.getNewPrice())
                        .containNum(qd.getQuantityPerCase() != null ? BigDecimal.valueOf(qd.getQuantityPerCase()) : null)
                        .janCode(qd.getJanCode())
                        .source("QUOTE_IMPORT")
                        .build());
            }
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * goodsNo が確定した商品の詳細レスポンスを構築します。
     */
    private ResponseEntity<EstimateGoodsSearchResponse> buildGoodsResponse(
            Integer shopNo, Integer goodsNo, String goodsCode,
            Integer partnerNo, Integer destinationNo) {

        // v_estimate_goods から価格情報を取得
        List<VEstimateGoods> goodsList = vEstimateGoodsService.findGoods(shopNo, List.of(goodsNo));
        IVEstimateGoods selected = null;

        // 特値チェック
        if (partnerNo != null) {
            List<VEstimateGoodsSpecial> specialList = vEstimateGoodsSpecialService.findGoods(
                    shopNo, List.of(goodsNo), partnerNo, destinationNo);
            if (!specialList.isEmpty()) {
                selected = specialList.getFirst();
            }
        }
        if (selected == null && !goodsList.isEmpty()) {
            selected = goodsList.getFirst();
        }

        // MGoods から specification と janCode を取得
        String specification = null;
        String janCode = null;
        MGoods mGoods = mGoodsService.getByGoodsNo(goodsNo);
        if (mGoods != null) {
            specification = mGoods.getSpecification();
            janCode = mGoods.getJanCode();
        }

        if (selected != null) {
            BigDecimal containNum = selected.getChangeContainNum() != null
                    ? selected.getChangeContainNum() : selected.getCaseContainNum();
            String pricePlanInfo = null;
            if (selected.getChangePlanDate() != null) {
                pricePlanInfo = selected.getChangePlanDate() + "より"
                        + selected.getBeforePrice() + "→" + selected.getAfterPrice();
            }
            return ResponseEntity.ok(EstimateGoodsSearchResponse.builder()
                    .goodsNo(goodsNo)
                    .goodsCode(goodsCode)
                    .goodsName(selected.getGoodsName())
                    .specification(specification)
                    .purchasePrice(selected.getPurchasePrice())
                    .containNum(containNum)
                    .changeContainNum(selected.getChangeContainNum())
                    .nowGoodsPrice(selected.getAfterPrice())
                    .pricePlanInfo(pricePlanInfo)
                    .janCode(janCode)
                    .source("GOODS")
                    .build());
        }

        // v_estimate_goods にない場合でも、商品マスタの情報で返す
        return ResponseEntity.ok(EstimateGoodsSearchResponse.builder()
                .goodsNo(goodsNo)
                .goodsCode(goodsCode)
                .goodsName(mGoods != null ? mGoods.getGoodsName() : "")
                .specification(specification)
                .janCode(janCode)
                .source("GOODS")
                .build());
    }
}
