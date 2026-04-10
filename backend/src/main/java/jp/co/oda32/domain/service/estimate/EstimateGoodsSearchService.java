package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.IVEstimateGoods;
import jp.co.oda32.domain.model.estimate.VEstimateGoods;
import jp.co.oda32.domain.model.estimate.VEstimateGoodsSpecial;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.model.purchase.TQuoteImportDetail;
import jp.co.oda32.domain.model.purchase.TQuoteImportHeader;
import jp.co.oda32.domain.repository.goods.GoodsRepository;
import jp.co.oda32.domain.repository.goods.MSalesGoodsRepository;
import jp.co.oda32.domain.repository.goods.WSalesGoodsRepository;
import jp.co.oda32.domain.repository.purchase.TQuoteImportDetailRepository;
import jp.co.oda32.domain.repository.purchase.TQuoteImportHeaderRepository;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MSalesGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.domain.service.purchase.MPurchasePriceChangePlanService;
import jp.co.oda32.dto.estimate.EstimateGoodsSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 見積商品検索サービス
 * 仕入価格変更予定・見積取込明細・販売商品マスタ/ワークから商品を検索する
 */
@Service
@RequiredArgsConstructor
public class EstimateGoodsSearchService {

    private final MGoodsService mGoodsService;
    private final MSalesGoodsService mSalesGoodsService;
    private final WSalesGoodsService wSalesGoodsService;
    private final MSupplierService mSupplierService;
    private final MPurchasePriceChangePlanService mPurchasePriceChangePlanService;
    private final VEstimateGoodsService vEstimateGoodsService;
    private final VEstimateGoodsSpecialService vEstimateGoodsSpecialService;
    private final TQuoteImportDetailRepository quoteImportDetailRepository;
    private final TQuoteImportHeaderRepository quoteImportHeaderRepository;
    private final MSalesGoodsRepository mSalesGoodsRepository;
    private final WSalesGoodsRepository wSalesGoodsRepository;
    private final GoodsRepository goodsRepository;

    /**
     * 仕入価格変更予定 + 見積取込明細の商品を商品名で検索します（ポップアップ検索用）。
     * 販売商品マスタに存在しないメーカー見積商品を検索できます。
     * paymentSupplierNo / makerNo を指定すると絞り込みます。
     * paymentSupplierNo は m_payment_supplier の PK で、紐づく全 m_supplier をグループとして検索します。
     * <p>
     * 注意: goodsName / paymentSupplierNo / makerNo がいずれも未指定の場合は
     * 全件取得を避けるため空リストを返します（パフォーマンス保護）。
     */
    public List<EstimateGoodsSearchResponse> searchPricePlanGoods(
            Integer shopNo, String goodsName, Integer paymentSupplierNo, Integer makerNo) {
        List<EstimateGoodsSearchResponse> response = new ArrayList<>();

        // 全条件未指定での全件取得を防止
        boolean hasGoodsName = goodsName != null && !goodsName.isBlank();
        if (!hasGoodsName && paymentSupplierNo == null && makerNo == null) {
            return response;
        }

        // paymentSupplierNo → グループ展開: 該当する全 m_supplier の supplier_code / supplier_no を取得
        Set<String> supplierCodeFilter = null;
        Set<Integer> supplierNoFilter = null;
        if (paymentSupplierNo != null) {
            List<MSupplier> siblings = mSupplierService.findByPaymentSupplierNo(shopNo, paymentSupplierNo);
            if (siblings.isEmpty()) {
                // 該当する仕入先が無ければ何も返さない
                return response;
            }
            supplierCodeFilter = siblings.stream()
                    .map(MSupplier::getSupplierCode)
                    .filter(c -> c != null && !c.isBlank())
                    .collect(Collectors.toSet());
            supplierNoFilter = siblings.stream()
                    .map(MSupplier::getSupplierNo)
                    .filter(n -> n != null)
                    .collect(Collectors.toSet());
        }

        // 1. 仕入価格変更予定（m_purchase_price_change_plan）
        List<MPurchasePriceChangePlan> plans = (goodsName != null && !goodsName.isBlank())
                ? mPurchasePriceChangePlanService.findByGoodsName(shopNo, goodsName)
                : mPurchasePriceChangePlanService.find(shopNo, null, null, null, null, null, null, Flag.NO);

        // supplierCode フィルタ（同グループの全 supplier_code が対象）
        if (supplierCodeFilter != null) {
            final Set<String> filterCodes = supplierCodeFilter;
            plans = plans.stream()
                    .filter(p -> p.getSupplierCode() != null && filterCodes.contains(p.getSupplierCode()))
                    .collect(Collectors.toList());
        }

        // 既存商品コード除外: m_sales_goods + w_sales_goods に存在する goods_code は仕入変更予定から除外
        // （マスタ未登録商品の救済表示が目的のため、既に登録されている商品は元々の販売商品側で表示する）
        // SQL projection で goods_code カラムだけを取得（17k件のEntityフルロードを回避）
        Set<String> existingCodes = new HashSet<>();
        existingCodes.addAll(mSalesGoodsRepository.findDistinctGoodsCodesByShopNo(shopNo));
        existingCodes.addAll(wSalesGoodsRepository.findDistinctGoodsCodesByShopNo(shopNo));
        if (!existingCodes.isEmpty()) {
            plans = plans.stream()
                    .filter(p -> p.getGoodsCode() == null || !existingCodes.contains(p.getGoodsCode()))
                    .collect(Collectors.toList());
        }

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

        // makerNo フィルタ用: jan_code → maker_no の解決
        // 全ての jan_code をまとめて引いて Map 化
        Map<String, Integer> janToMakerNo = makerNo == null ? Map.of() : buildJanToMakerMap(uniquePlans.values().stream()
                .map(MPurchasePriceChangePlan::getJanCode)
                .filter(j -> j != null && !j.isBlank())
                .collect(Collectors.toList()));

        Set<String> addedKeys = new HashSet<>();
        for (MPurchasePriceChangePlan plan : uniquePlans.values()) {
            // makerNo フィルタ: jan_code から MGoods を引いて maker_no を比較
            if (makerNo != null) {
                Integer planMakerNo = plan.getJanCode() != null ? janToMakerNo.get(plan.getJanCode()) : null;
                if (planMakerNo == null || !makerNo.equals(planMakerNo)) {
                    continue;
                }
            }

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
            String escapedName = goodsName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            List<TQuoteImportDetail> quoteDetails = quoteImportDetailRepository
                    .findByGoodsNameContainingAndStatus(escapedName, "PENDING");

            // supplierNoFilter 用: 親ヘッダーの supplier_no を見るため、ヘッダーを一括取得
            Map<Integer, TQuoteImportHeader> headerMap = Map.of();
            if (supplierNoFilter != null && !quoteDetails.isEmpty()) {
                List<Integer> headerIds = quoteDetails.stream()
                        .map(TQuoteImportDetail::getQuoteImportId)
                        .distinct()
                        .collect(Collectors.toList());
                headerMap = quoteImportHeaderRepository.findAllById(headerIds).stream()
                        .collect(Collectors.toMap(TQuoteImportHeader::getQuoteImportId, h -> h));
            }

            // makerNo フィルタ用: jan_code → maker_no の解決
            Map<String, Integer> janToMakerNoQI = makerNo == null ? Map.of() : buildJanToMakerMap(quoteDetails.stream()
                    .map(TQuoteImportDetail::getJanCode)
                    .filter(j -> j != null && !j.isBlank())
                    .collect(Collectors.toList()));

            for (TQuoteImportDetail qd : quoteDetails) {
                // supplierNo フィルタ（同グループの全 supplier_no が対象）
                if (supplierNoFilter != null) {
                    TQuoteImportHeader header = headerMap.get(qd.getQuoteImportId());
                    if (header == null || header.getSupplierNo() == null
                            || !supplierNoFilter.contains(header.getSupplierNo())) {
                        continue;
                    }
                }
                // makerNo フィルタ
                if (makerNo != null) {
                    Integer qdMakerNo = qd.getJanCode() != null ? janToMakerNoQI.get(qd.getJanCode()) : null;
                    if (qdMakerNo == null || !makerNo.equals(qdMakerNo)) {
                        continue;
                    }
                }

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

        return response;
    }

    /**
     * 後方互換: 既存呼び出し用（supplierNo/makerNo なし）
     */
    public List<EstimateGoodsSearchResponse> searchPricePlanGoods(Integer shopNo, String goodsName) {
        return searchPricePlanGoods(shopNo, goodsName, null, null);
    }

    /**
     * jan_code リストから { jan_code → maker_no } の Map を構築します。
     * 同一 jan_code が複数 m_goods に存在する場合は更新日時降順で最新の有効な maker_no を採用します。
     * IN クエリで一括取得（N+1 回避）。
     */
    private Map<String, Integer> buildJanToMakerMap(List<String> janCodes) {
        if (janCodes == null || janCodes.isEmpty()) return Map.of();
        Set<String> uniqueJans = new HashSet<>(janCodes);
        // SQL 側で modify_date_time DESC ソート済み → 最初に出てきた jan_code が最新
        List<MGoods> allGoods = goodsRepository.findByJanCodeInAndDelFlgNo(uniqueJans);
        Map<String, Integer> result = new HashMap<>();
        for (MGoods g : allGoods) {
            if (g.getJanCode() == null || g.getMakerNo() == null) continue;
            // putIfAbsent で最初（=最新）のものだけ採用
            result.putIfAbsent(g.getJanCode(), g.getMakerNo());
        }
        return result;
    }

    /**
     * 商品コードまたはJANコードで商品情報を取得します（明細行の入力時に使用）。
     *
     * 入力値の文字数で分岐:
     * - 8文字以下: 商品コードとして検索（販売商品マスタ → ワーク）
     * - 9文字以上: JANコードとして検索（商品マスタ → 仕入価格変更予定）
     *
     * @return 検索結果。見つからない場合は Optional.empty()
     */
    public Optional<EstimateGoodsSearchResponse> searchGoods(
            Integer shopNo, String code, Integer partnerNo, Integer destinationNo) {

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
            // 同一 jan_code が複数 m_goods に登録されているケースがあるため
            // 更新日時降順で最新の有効レコードを採用する
            List<MGoods> byJanList = mGoodsService.find(null, null, null, code, null, Flag.NO);
            MGoods byJan = byJanList.stream()
                    .sorted(Comparator
                            .comparing(MGoods::getModifyDateTime,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(MGoods::getAddDateTime,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                    .findFirst()
                    .orElse(null);
            if (byJan != null) {
                foundGoodsNo = byJan.getGoodsNo();
                MSalesGoods sg = mSalesGoodsService.getByPK(shopNo, byJan.getGoodsNo());
                foundGoodsCode = sg != null ? sg.getGoodsCode() : code;
            }
        }

        // 販売商品で見つかった場合
        if (foundGoodsNo != null) {
            return Optional.of(buildGoodsResponse(shopNo, foundGoodsNo, foundGoodsCode, partnerNo, destinationNo));
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
                return Optional.of(EstimateGoodsSearchResponse.builder()
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
                return Optional.of(EstimateGoodsSearchResponse.builder()
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

        return Optional.empty();
    }

    /**
     * goodsNo が確定した商品の詳細レスポンスを構築します。
     */
    private EstimateGoodsSearchResponse buildGoodsResponse(
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

        // WSalesGoods → MSalesGoods の順で仕入先番号を取得（ワークが最新）
        Integer supplierNo = null;
        WSalesGoods workGoods = wSalesGoodsService.getByPK(shopNo, goodsNo);
        if (workGoods != null) {
            supplierNo = workGoods.getSupplierNo();
        } else {
            MSalesGoods salesGoods = mSalesGoodsService.getByPK(shopNo, goodsNo);
            if (salesGoods != null) {
                supplierNo = salesGoods.getSupplierNo();
            }
        }

        if (selected != null) {
            BigDecimal containNum = selected.getChangeContainNum() != null
                    ? selected.getChangeContainNum() : selected.getCaseContainNum();
            String pricePlanInfo = null;
            if (selected.getChangePlanDate() != null) {
                pricePlanInfo = selected.getChangePlanDate() + "より"
                        + selected.getBeforePrice() + "→" + selected.getAfterPrice();
            }
            return EstimateGoodsSearchResponse.builder()
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
                    .supplierNo(supplierNo)
                    .build();
        }

        // v_estimate_goods にない場合でも、商品マスタの情報で返す
        return EstimateGoodsSearchResponse.builder()
                .goodsNo(goodsNo)
                .goodsCode(goodsCode)
                .goodsName(mGoods != null ? mGoods.getGoodsName() : "")
                .specification(specification)
                .janCode(janCode)
                .source("GOODS")
                .supplierNo(supplierNo)
                .build();
    }
}
