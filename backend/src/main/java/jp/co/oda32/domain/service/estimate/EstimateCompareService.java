package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.domain.model.estimate.IVEstimateGoods;
import jp.co.oda32.domain.model.estimate.VEstimateGoods;
import jp.co.oda32.domain.model.estimate.VEstimateGoodsSpecial;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MSalesGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.dto.estimate.CompareGoodsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstimateCompareService {

    private final VEstimateGoodsService vEstimateGoodsService;
    private final VEstimateGoodsSpecialService vEstimateGoodsSpecialService;
    private final MGoodsService mGoodsService;
    private final MSalesGoodsService mSalesGoodsService;
    private final WSalesGoodsService wSalesGoodsService;
    private final MSupplierService mSupplierService;

    @Transactional(readOnly = true)
    public List<CompareGoodsResponse> compareGoods(
            Integer shopNo, List<Integer> goodsNoList,
            Integer partnerNo, Integer destinationNo) {
        // 防御的バリデーション（Service が Controller 以外から呼ばれる可能性に備える）
        if (shopNo == null || goodsNoList == null || goodsNoList.isEmpty()) {
            return new ArrayList<>();
        }

        // ==== Batch fetch all reference data (avoid N+1) ====
        // 1. Price info from views
        List<VEstimateGoods> normalGoods = vEstimateGoodsService.findGoods(shopNo, goodsNoList);
        Map<Integer, VEstimateGoods> normalMap = normalGoods.stream()
                .collect(Collectors.toMap(VEstimateGoods::getGoodsNo, Function.identity(), (a, b) -> a));

        Map<Integer, VEstimateGoodsSpecial> specialMap = Map.of();
        if (partnerNo != null) {
            List<VEstimateGoodsSpecial> specialGoods = vEstimateGoodsSpecialService.findGoods(
                    shopNo, goodsNoList, partnerNo, destinationNo);
            specialMap = specialGoods.stream()
                    .collect(Collectors.toMap(VEstimateGoodsSpecial::getGoodsNo, Function.identity(), (a, b) -> a));
        }

        // 2. Goods master (single batch query)
        Map<Integer, MGoods> goodsMap = mGoodsService.findByGoodsNoList(goodsNoList).stream()
                .collect(Collectors.toMap(MGoods::getGoodsNo, Function.identity(), (a, b) -> a));

        // 3. Sales goods (single batch query)
        Map<Integer, MSalesGoods> salesGoodsMap = mSalesGoodsService.findByGoodsNoList(goodsNoList).stream()
                .filter(sg -> shopNo.equals(sg.getShopNo()))
                .collect(Collectors.toMap(MSalesGoods::getGoodsNo, Function.identity(), (a, b) -> a));

        // 4. Work sales goods (single batch query)
        Map<Integer, WSalesGoods> workGoodsMap = wSalesGoodsService.findByGoodsNoList(goodsNoList).stream()
                .filter(wg -> shopNo.equals(wg.getShopNo()))
                .collect(Collectors.toMap(WSalesGoods::getGoodsNo, Function.identity(), (a, b) -> a));

        // 5. Collect supplierNos from sales/work goods, then batch fetch suppliers
        Set<Integer> supplierNos = new HashSet<>();
        for (Integer gn : goodsNoList) {
            WSalesGoods w = workGoodsMap.get(gn);
            MSalesGoods s = salesGoodsMap.get(gn);
            if (w != null && w.getSupplierNo() != null) {
                supplierNos.add(w.getSupplierNo());
            } else if (s != null && s.getSupplierNo() != null) {
                supplierNos.add(s.getSupplierNo());
            }
        }
        Map<Integer, MSupplier> supplierMap = mSupplierService.findBySupplierNoList(new ArrayList<>(supplierNos)).stream()
                .collect(Collectors.toMap(MSupplier::getSupplierNo, Function.identity(), (a, b) -> a));

        List<CompareGoodsResponse> result = new ArrayList<>();
        for (Integer goodsNo : goodsNoList) {
            // Determine price source: special (tokune) > normal
            VEstimateGoodsSpecial specialSource = specialMap.getOrDefault(goodsNo, null);
            VEstimateGoods normalSource = normalMap.getOrDefault(goodsNo, null);
            IVEstimateGoods priceSource = specialSource != null ? specialSource : normalSource;

            // Get goods master info from pre-fetched map
            MGoods mGoods = goodsMap.get(goodsNo);
            String specification = mGoods != null ? mGoods.getSpecification() : null;
            String janCode = mGoods != null ? mGoods.getJanCode() : null;
            String makerName = (mGoods != null && mGoods.getMaker() != null) ? mGoods.getMaker().getMakerName() : null;

            // Get supplier info (work takes priority over master)
            Integer supplierNo = null;
            String supplierName = null;
            WSalesGoods workGoods = workGoodsMap.get(goodsNo);
            MSalesGoods salesGoods = salesGoodsMap.get(goodsNo);
            if (workGoods != null) {
                supplierNo = workGoods.getSupplierNo();
            } else if (salesGoods != null) {
                supplierNo = salesGoods.getSupplierNo();
            }
            if (supplierNo != null) {
                MSupplier supplier = supplierMap.get(supplierNo);
                if (supplier != null) {
                    supplierName = supplier.getSupplierName();
                }
            }

            // Resolve goods code from sales goods or work
            String goodsCode = null;
            if (salesGoods != null) {
                goodsCode = salesGoods.getGoodsCode();
            } else if (workGoods != null) {
                goodsCode = workGoods.getGoodsCode();
            }

            // Resolve nowGoodsPrice from the concrete view entity (not IVEstimateGoods interface)
            BigDecimal nowGoodsPrice = null;
            if (specialSource != null) {
                nowGoodsPrice = specialSource.getNowGoodsPrice();
            } else if (normalSource != null) {
                nowGoodsPrice = normalSource.getNowGoodsPrice();
            }

            if (priceSource != null) {
                BigDecimal containNum = priceSource.getChangeContainNum() != null
                        ? priceSource.getChangeContainNum() : priceSource.getCaseContainNum();
                String pricePlanInfo = null;
                BigDecimal planAfterPrice = null;
                if (priceSource.getChangePlanDate() != null) {
                    pricePlanInfo = priceSource.getChangePlanDate() + "より"
                            + priceSource.getBeforePrice() + "→" + priceSource.getAfterPrice();
                    planAfterPrice = priceSource.getAfterPrice();
                }

                result.add(CompareGoodsResponse.builder()
                        .goodsNo(goodsNo)
                        .goodsCode(goodsCode != null ? goodsCode : priceSource.getGoodsCode())
                        .goodsName(priceSource.getGoodsName())
                        .specification(specification)
                        .janCode(janCode)
                        .makerName(makerName)
                        .supplierName(supplierName)
                        .supplierNo(supplierNo)
                        .purchasePrice(priceSource.getPurchasePrice())
                        .nowGoodsPrice(nowGoodsPrice)
                        .containNum(containNum)
                        .changeContainNum(priceSource.getChangeContainNum())
                        .pricePlanInfo(pricePlanInfo)
                        .planAfterPrice(planAfterPrice)
                        .build());
            } else {
                // No price info available, use goods master only
                result.add(CompareGoodsResponse.builder()
                        .goodsNo(goodsNo)
                        .goodsCode(goodsCode)
                        .goodsName(mGoods != null ? mGoods.getGoodsName() : "")
                        .specification(specification)
                        .janCode(janCode)
                        .makerName(makerName)
                        .supplierName(supplierName)
                        .supplierNo(supplierNo)
                        .build());
            }
        }
        return result;
    }
}
