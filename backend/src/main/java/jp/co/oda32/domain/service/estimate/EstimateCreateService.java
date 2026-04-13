package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.constant.EstimateStatus;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.purchase.TQuoteImportDetail;
import jp.co.oda32.domain.model.purchase.TQuoteImportHeader;
import jp.co.oda32.domain.repository.purchase.TQuoteImportDetailRepository;
import jp.co.oda32.domain.repository.purchase.TQuoteImportHeaderRepository;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.service.login.LoginUserService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.dto.estimate.EstimateCreateRequest;
import jp.co.oda32.dto.estimate.EstimateDetailCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 見積作成・修正・削除サービス
 * 親子得意先間の見積同期処理を含む
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class EstimateCreateService {

    private final TEstimateService tEstimateService;
    private final TEstimateDetailService tEstimateDetailService;
    private final MPartnerService mPartnerService;
    private final MSupplierService mSupplierService;
    private final LoginUserService loginUserService;
    private final TQuoteImportHeaderRepository quoteImportHeaderRepository;
    private final TQuoteImportDetailRepository quoteImportDetailRepository;

    /**
     * 見積を新規作成します。親子得意先の同期も実行します。
     */
    @Transactional
    public TEstimate createEstimate(EstimateCreateRequest request) throws Exception {
        MPartner partner = mPartnerService.getByPartnerNo(request.getPartnerNo());
        Integer companyNo = partner != null ? partner.getCompanyNo() : null;
        boolean isIncludeTaxDisplay = partner != null && partner.isIncludeTaxDisplay();

        TEstimate estimate = TEstimate.builder()
                .shopNo(request.getShopNo())
                .partnerNo(request.getPartnerNo())
                .destinationNo(request.getDestinationNo() != null ? request.getDestinationNo() : 0)
                .estimateDate(request.getEstimateDate())
                .priceChangeDate(request.getPriceChangeDate())
                .note(request.getNote())
                .requirement(request.getRequirement())
                .recipientName(request.getRecipientName())
                .proposalMessage(request.getProposalMessage())
                .estimateStatus(EstimateStatus.CREATE.getCode())
                .companyNo(companyNo)
                .isIncludeTaxDisplay(isIncludeTaxDisplay)
                .build();

        TEstimate saved = tEstimateService.insert(estimate);
        insertDetails(saved.getEstimateNo(), request.getShopNo(), companyNo, request.getDetails());

        // 新規商品を仕入先見積データに自動登録
        registerNewGoodsToQuoteImport(request);

        // 親子得意先同期
        syncParentChildEstimates(saved, partner, null);

        return tEstimateService.getByEstimateNoWithDetails(saved.getEstimateNo());
    }

    /**
     * 見積を修正します。親子得意先の同期も実行します。
     */
    @Transactional
    public TEstimate updateEstimate(Integer estimateNo, EstimateCreateRequest request) throws Exception {
        TEstimate estimate = tEstimateService.getByEstimateNo(estimateNo);
        MPartner partner = mPartnerService.getByPartnerNo(request.getPartnerNo());
        Integer companyNo = partner != null ? partner.getCompanyNo() : null;

        var beforePriceChangeDate = estimate.getPriceChangeDate();

        estimate.setShopNo(request.getShopNo());
        estimate.setPartnerNo(request.getPartnerNo());
        estimate.setDestinationNo(request.getDestinationNo() != null ? request.getDestinationNo() : 0);
        estimate.setEstimateDate(request.getEstimateDate());
        estimate.setPriceChangeDate(request.getPriceChangeDate());
        estimate.setNote(request.getNote());
        estimate.setRequirement(request.getRequirement());
        estimate.setRecipientName(request.getRecipientName());
        estimate.setProposalMessage(request.getProposalMessage());
        estimate.setEstimateStatus(EstimateStatus.MODIFIED.getCode());
        estimate.setCompanyNo(companyNo);

        tEstimateService.update(estimate);

        // 明細は delete-insert
        tEstimateDetailService.delete(estimateNo);
        insertDetails(estimateNo, request.getShopNo(), companyNo, request.getDetails());

        // 新規商品（goodsNo=null + supplierNo あり）を仕入先見積データに自動登録
        registerNewGoodsToQuoteImport(request);

        // 親子得意先同期
        syncParentChildEstimates(estimate, partner, beforePriceChangeDate);

        return tEstimateService.getByEstimateNoWithDetails(estimateNo);
    }

    /**
     * 見積を論理削除します。
     */
    @Transactional
    public void deleteEstimate(Integer estimateNo) throws Exception {
        TEstimate estimate = tEstimateService.getByEstimateNo(estimateNo);
        estimate.setEstimateStatus(EstimateStatus.DELETE.getCode());
        tEstimateDetailService.deleteByDelFlg(estimateNo);
        tEstimateService.delete(estimate);
    }

    private void insertDetails(Integer estimateNo, Integer shopNo, Integer companyNo,
                               List<EstimateDetailCreateRequest> details) throws Exception {
        List<TEstimateDetail> detailEntities = new ArrayList<>();
        int detailNo = 1;
        for (EstimateDetailCreateRequest d : details) {
            TEstimateDetail detail = TEstimateDetail.builder()
                    .estimateNo(estimateNo)
                    .estimateDetailNo(detailNo++)
                    .shopNo(shopNo)
                    .companyNo(companyNo)
                    .goodsNo(d.getGoodsNo())
                    .goodsCode(d.getGoodsCode())
                    .goodsName(d.getGoodsName())
                    .specification(d.getSpecification())
                    .goodsPrice(d.getGoodsPrice())
                    .purchasePrice(d.getPurchasePrice())
                    .containNum(d.getContainNum())
                    .changeContainNum(d.getChangeContainNum())
                    .profitRate(d.getProfitRate())
                    .detailNote(d.getDetailNote())
                    .displayOrder(d.getDisplayOrder())
                    .build();
            detailEntities.add(detail);
        }
        tEstimateDetailService.insert(detailEntities);
    }

    /**
     * 新規商品（goodsNo=null かつ supplierNo あり）を仕入先見積データに自動登録します。
     * 仕入先番号ごとにグルーピングし、ヘッダー＋明細を作成します。
     */
    private void registerNewGoodsToQuoteImport(EstimateCreateRequest request) {
        List<EstimateDetailCreateRequest> newGoodsDetails = request.getDetails().stream()
                .filter(d -> d.getGoodsNo() == null && d.getSupplierNo() != null)
                .toList();

        if (newGoodsDetails.isEmpty()) return;

        // 仕入先番号ごとにグルーピング
        Map<Integer, List<EstimateDetailCreateRequest>> bySupplier = newGoodsDetails.stream()
                .collect(Collectors.groupingBy(EstimateDetailCreateRequest::getSupplierNo));

        Timestamp now = Timestamp.from(Instant.now());
        Integer loginUserNo = null;
        try {
            var loginUser = loginUserService.getLoginUser();
            loginUserNo = loginUser.getLoginUserNo();
        } catch (Exception e) {
            log.warn("ログインユーザー取得失敗（監査フィールド未設定）", e);
        }

        for (Map.Entry<Integer, List<EstimateDetailCreateRequest>> entry : bySupplier.entrySet()) {
            Integer supplierNo = entry.getKey();
            List<EstimateDetailCreateRequest> details = entry.getValue();

            MSupplier supplier = mSupplierService.getBySupplierNo(supplierNo);
            String supplierName = supplier != null ? supplier.getSupplierName() : "";
            String supplierCode = supplier != null ? supplier.getSupplierCode() : null;

            TQuoteImportHeader header = TQuoteImportHeader.builder()
                    .shopNo(request.getShopNo())
                    .supplierName(supplierName)
                    .supplierCode(supplierCode)
                    .supplierNo(supplierNo)
                    .fileName("見積作成時自動登録")
                    .effectiveDate(request.getPriceChangeDate())
                    .totalCount(details.size())
                    .delFlg(Flag.NO.getValue())
                    .addDateTime(now)
                    .addUserNo(loginUserNo)
                    .build();
            TQuoteImportHeader savedHeader = quoteImportHeaderRepository.save(header);

            int rowNo = 1;
            for (EstimateDetailCreateRequest d : details) {
                String code = d.getGoodsCode();
                TQuoteImportDetail detail = TQuoteImportDetail.builder()
                        .quoteImportId(savedHeader.getQuoteImportId())
                        .rowNo(rowNo++)
                        .janCode(code != null && code.length() > 8 ? code : null)
                        .quoteGoodsName(d.getGoodsName())
                        .quoteGoodsCode(code != null && code.length() <= 8 ? code : null)
                        .specification(d.getSpecification())
                        // containNum は通常整数だが BigDecimal なので setScale(0, HALF_UP) で四捨五入
                        .quantityPerCase(d.getContainNum() != null
                                ? d.getContainNum().setScale(0, java.math.RoundingMode.HALF_UP).intValue()
                                : null)
                        .newPrice(d.getPurchasePrice())
                        .status("PENDING")
                        .addDateTime(now)
                        .build();
                quoteImportDetailRepository.save(detail);
            }

            log.info("仕入先見積データ自動登録: 仕入先={}, 件数={}, quoteImportId={}",
                    supplierName, details.size(), savedHeader.getQuoteImportId());
        }
    }

    /**
     * 親子得意先間の見積同期を実行します。
     *
     * @param estimate              保存済み見積
     * @param partner               得意先
     * @param beforePriceChangeDate 修正前の価格変更日（新規作成時はnull）
     */
    private void syncParentChildEstimates(TEstimate estimate, MPartner partner,
                                          java.time.LocalDate beforePriceChangeDate) throws Exception {
        if (partner == null) return;

        if (partner.getParentPartnerNo() != null) {
            // 子得意先の見積を登録した → 親と兄弟に同期
            syncToParentAndSiblings(estimate, partner, beforePriceChangeDate);
        } else {
            // 親得意先（または単独）の見積を登録した → 子に同期
            List<MPartner> children = mPartnerService.findChildrenPartner(partner.getPartnerNo());
            if (!children.isEmpty()) {
                syncToChildren(estimate, children, beforePriceChangeDate);
            }
        }
    }

    /**
     * 子得意先の見積を親得意先と他の兄弟得意先に同期します。
     */
    private void syncToParentAndSiblings(TEstimate childEstimate, MPartner childPartner,
                                         java.time.LocalDate beforePriceChangeDate) throws Exception {
        Integer parentPartnerNo = childPartner.getParentPartnerNo();
        MPartner parentPartner = mPartnerService.getByPartnerNo(parentPartnerNo);
        if (parentPartner == null) return;

        var searchDate = beforePriceChangeDate != null ? beforePriceChangeDate : childEstimate.getPriceChangeDate();

        // 親見積を検索
        List<TEstimate> parentEstimates = tEstimateService.find(
                childEstimate.getShopNo(), parentPartnerNo, searchDate,
                EstimateStatus.getBeforeNotifiedStatusCodeList());

        if (parentEstimates.isEmpty()) {
            // 親見積が存在しない → 新規作成
            createSyncedEstimate(childEstimate, parentPartner);
        } else {
            // 親見積が存在する → 更新
            for (TEstimate parentEstimate : parentEstimates) {
                updateSyncedEstimate(parentEstimate, childEstimate);
            }
        }

        // 他の子得意先にも同期
        List<MPartner> siblings = mPartnerService.findChildrenPartner(parentPartnerNo);
        syncToChildren(childEstimate, siblings.stream()
                .filter(s -> !s.getPartnerNo().equals(childPartner.getPartnerNo()))
                .toList(), beforePriceChangeDate);
    }

    /**
     * 親得意先の見積を全子得意先に同期します。
     */
    private void syncToChildren(TEstimate parentEstimate, List<MPartner> children,
                                java.time.LocalDate beforePriceChangeDate) throws Exception {
        var searchDate = beforePriceChangeDate != null ? beforePriceChangeDate : parentEstimate.getPriceChangeDate();

        for (MPartner child : children) {
            List<TEstimate> childEstimates = tEstimateService.find(
                    parentEstimate.getShopNo(), child.getPartnerNo(), searchDate,
                    EstimateStatus.getBeforeNotifiedStatusCodeList());

            if (childEstimates.isEmpty()) {
                createSyncedEstimate(parentEstimate, child);
            } else {
                for (TEstimate childEstimate : childEstimates) {
                    updateSyncedEstimate(childEstimate, parentEstimate);
                    childEstimate.setEstimateStatus(EstimateStatus.OTHER_PARTNER_NOTIFIED.getCode());
                    tEstimateService.update(childEstimate);
                }
            }
        }
    }

    /**
     * ソース見積の内容をコピーして、指定得意先向けの新規見積を作成します。
     * 案件共通項目（note, requirement）はコピーしますが、得意先固有項目
     * （recipientName: 担当者宛名, proposalMessage: 提案文, destinationNo: 配送先）はコピーしません。
     */
    private void createSyncedEstimate(TEstimate sourceEstimate, MPartner targetPartner) throws Exception {
        TEstimate newEstimate = TEstimate.builder()
                .shopNo(sourceEstimate.getShopNo())
                .partnerNo(targetPartner.getPartnerNo())
                .destinationNo(0) // 配送先は得意先固有のため引き継がない
                .estimateDate(sourceEstimate.getEstimateDate())
                .priceChangeDate(sourceEstimate.getPriceChangeDate())
                .note(sourceEstimate.getNote())
                .requirement(sourceEstimate.getRequirement())
                // recipientName, proposalMessage は得意先固有のため引き継がない
                .estimateStatus(EstimateStatus.OTHER_PARTNER_NOTIFIED.getCode())
                .companyNo(targetPartner.getCompanyNo())
                .isIncludeTaxDisplay(targetPartner.isIncludeTaxDisplay())
                .build();

        TEstimate saved = tEstimateService.insert(newEstimate);
        copyDetails(sourceEstimate.getEstimateNo(), saved.getEstimateNo(),
                saved.getShopNo(), saved.getCompanyNo());
    }

    /**
     * ターゲット見積の明細をソース見積の明細で置き換えます。
     * 案件共通項目（note, requirement）は同期しますが、得意先固有項目
     * （recipientName, proposalMessage, destinationNo）は同期せずターゲット側の既存値を保持します。
     */
    private void updateSyncedEstimate(TEstimate targetEstimate, TEstimate sourceEstimate) throws Exception {
        targetEstimate.setPriceChangeDate(sourceEstimate.getPriceChangeDate());
        targetEstimate.setEstimateDate(sourceEstimate.getEstimateDate());
        targetEstimate.setNote(sourceEstimate.getNote());
        targetEstimate.setRequirement(sourceEstimate.getRequirement());
        // recipientName, proposalMessage, destinationNo はターゲット側の既存値を保持
        tEstimateService.update(targetEstimate);

        tEstimateDetailService.delete(targetEstimate.getEstimateNo());
        copyDetails(sourceEstimate.getEstimateNo(), targetEstimate.getEstimateNo(),
                targetEstimate.getShopNo(), targetEstimate.getCompanyNo());
    }

    /**
     * ソース見積の明細をターゲット見積にコピーします。
     */
    private void copyDetails(Integer sourceEstimateNo, Integer targetEstimateNo,
                             Integer shopNo, Integer companyNo) throws Exception {
        List<TEstimateDetail> sourceDetails = tEstimateDetailService.findByEstimateNo(sourceEstimateNo);
        sourceDetails.sort(Comparator.comparingInt(TEstimateDetail::getDisplayOrder));

        List<TEstimateDetail> newDetails = new ArrayList<>();
        int detailNo = 1;
        for (TEstimateDetail src : sourceDetails) {
            TEstimateDetail copy = TEstimateDetail.builder()
                    .estimateNo(targetEstimateNo)
                    .estimateDetailNo(detailNo++)
                    .shopNo(shopNo)
                    .companyNo(companyNo)
                    .goodsNo(src.getGoodsNo())
                    .goodsCode(src.getGoodsCode())
                    .goodsName(src.getGoodsName())
                    .specification(src.getSpecification())
                    .goodsPrice(src.getGoodsPrice())
                    .purchasePrice(src.getPurchasePrice())
                    .containNum(src.getContainNum())
                    .changeContainNum(src.getChangeContainNum())
                    .profitRate(src.getProfitRate())
                    .detailNote(src.getDetailNote())
                    .displayOrder(detailNo - 1)
                    .build();
            newDetails.add(copy);
        }
        tEstimateDetailService.insert(newDetails);
    }
}
