package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TComparisonDetail;
import jp.co.oda32.domain.model.estimate.TComparisonGroup;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.model.estimate.TEstimateComparison;
import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.dto.comparison.ComparisonCreateRequest;
import jp.co.oda32.dto.comparison.ComparisonDetailCreateRequest;
import jp.co.oda32.dto.comparison.ComparisonGroupCreateRequest;
import jp.co.oda32.domain.service.login.LoginUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EstimateComparisonCreateService {

    private final TEstimateComparisonService comparisonService;
    private final TEstimateService estimateService;
    private final LoginUserService loginUserService;

    public TEstimate getEstimate(Integer estimateNo) {
        return estimateService.getByEstimateNoWithDetails(estimateNo);
    }

    @Transactional
    public TEstimateComparison createComparison(ComparisonCreateRequest request) throws Exception {
        Integer companyNo = loginUserService.getLoginUser().getCompanyNo();

        TEstimateComparison header = TEstimateComparison.builder()
                .shopNo(request.getShopNo())
                .partnerNo(request.getPartnerNo())
                .destinationNo(request.getDestinationNo())
                .comparisonDate(request.getComparisonDate())
                .comparisonStatus("00")
                .title(request.getTitle())
                .note(request.getNote())
                .companyNo(companyNo)
                .build();
        TEstimateComparison saved = comparisonService.insertHeader(header);
        Integer comparisonNo = saved.getComparisonNo();

        insertGroupsAndDetails(comparisonNo, request.getShopNo(), companyNo, request.getGroups());

        return comparisonService.getByComparisonNo(comparisonNo);
    }

    @Transactional
    public TEstimateComparison updateComparison(Integer comparisonNo, ComparisonCreateRequest request) throws Exception {
        TEstimateComparison existing = comparisonService.getByComparisonNo(comparisonNo);
        if (existing == null) {
            throw new IllegalArgumentException("比較見積が見つかりません: " + comparisonNo);
        }

        // 全グループ・明細を物理削除（softDelete だと PK 衝突するため）
        comparisonService.physicalDeleteGroupsAndDetails(comparisonNo);

        // ヘッダ更新
        existing.setShopNo(request.getShopNo());
        existing.setPartnerNo(request.getPartnerNo());
        existing.setDestinationNo(request.getDestinationNo());
        existing.setComparisonDate(request.getComparisonDate());
        existing.setTitle(request.getTitle());
        existing.setNote(request.getNote());
        comparisonService.updateHeader(existing);

        // 再 insert
        Integer companyNo = loginUserService.getLoginUser().getCompanyNo();
        insertGroupsAndDetails(comparisonNo, request.getShopNo(), companyNo, request.getGroups());

        return comparisonService.getByComparisonNo(comparisonNo);
    }

    @Transactional
    public TEstimateComparison createFromEstimate(Integer estimateNo) throws Exception {
        TEstimate estimate = estimateService.getByEstimateNoWithDetails(estimateNo);
        if (estimate == null) {
            throw new IllegalArgumentException("見積が見つかりません: " + estimateNo);
        }

        Integer companyNo = loginUserService.getLoginUser().getCompanyNo();

        TEstimateComparison header = TEstimateComparison.builder()
                .shopNo(estimate.getShopNo())
                .partnerNo(estimate.getPartnerNo())
                .destinationNo(estimate.getDestinationNo())
                .comparisonDate(LocalDate.now())
                .comparisonStatus("00")
                .sourceEstimateNo(estimateNo)
                .title(null)
                .note(null)
                .companyNo(companyNo)
                .build();
        TEstimateComparison saved = comparisonService.insertHeader(header);
        Integer comparisonNo = saved.getComparisonNo();

        // 各見積明細 → 基準品グループ
        List<TEstimateDetail> details = estimate.getTEstimateDetailList();
        int groupNo = 1;
        for (TEstimateDetail d : details) {
            TComparisonGroup group = TComparisonGroup.builder()
                    .comparisonNo(comparisonNo)
                    .groupNo(groupNo)
                    .baseGoodsNo(d.getGoodsNo())
                    .baseGoodsCode(d.getGoodsCode())
                    .baseGoodsName(d.getGoodsName() != null ? d.getGoodsName() : "")
                    .baseSpecification(d.getSpecification())
                    .basePurchasePrice(d.getPurchasePrice())
                    .baseGoodsPrice(d.getGoodsPrice())
                    .baseContainNum(d.getChangeContainNum() != null ? d.getChangeContainNum() : d.getContainNum())
                    .displayOrder(groupNo)
                    .shopNo(estimate.getShopNo())
                    .companyNo(companyNo)
                    .build();
            comparisonService.insertGroup(group);
            groupNo++;
        }

        return comparisonService.getByComparisonNo(comparisonNo);
    }

    private void insertGroupsAndDetails(Integer comparisonNo, Integer shopNo, Integer companyNo,
                                         List<ComparisonGroupCreateRequest> groups) throws Exception {
        int groupNo = 1;
        for (ComparisonGroupCreateRequest g : groups) {
            TComparisonGroup group = TComparisonGroup.builder()
                    .comparisonNo(comparisonNo)
                    .groupNo(groupNo)
                    .baseGoodsNo(g.getBaseGoodsNo())
                    .baseGoodsCode(g.getBaseGoodsCode())
                    .baseGoodsName(g.getBaseGoodsName())
                    .baseSpecification(g.getBaseSpecification())
                    .basePurchasePrice(g.getBasePurchasePrice())
                    .baseGoodsPrice(g.getBaseGoodsPrice())
                    .baseContainNum(g.getBaseContainNum())
                    .displayOrder(g.getDisplayOrder() > 0 ? g.getDisplayOrder() : groupNo)
                    .groupNote(g.getGroupNote())
                    .shopNo(shopNo)
                    .companyNo(companyNo)
                    .build();
            comparisonService.insertGroup(group);

            if (g.getDetails() != null) {
                int detailNo = 1;
                for (ComparisonDetailCreateRequest d : g.getDetails()) {
                    TComparisonDetail detail = TComparisonDetail.builder()
                            .comparisonNo(comparisonNo)
                            .groupNo(groupNo)
                            .detailNo(detailNo)
                            .goodsNo(d.getGoodsNo())
                            .goodsCode(d.getGoodsCode())
                            .goodsName(d.getGoodsName())
                            .specification(d.getSpecification())
                            .purchasePrice(d.getPurchasePrice())
                            .proposedPrice(d.getProposedPrice())
                            .containNum(d.getContainNum())
                            .detailNote(d.getDetailNote())
                            .displayOrder(d.getDisplayOrder() > 0 ? d.getDisplayOrder() : detailNo)
                            .supplierNo(d.getSupplierNo())
                            .shopNo(shopNo)
                            .companyNo(companyNo)
                            .build();
                    comparisonService.insertDetail(detail);
                    detailNo++;
                }
            }
            groupNo++;
        }
    }
}
