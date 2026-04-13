package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.model.purchase.TQuoteImportHeader;
import jp.co.oda32.domain.model.purchase.TQuoteImportDetail;
import jp.co.oda32.domain.repository.purchase.TQuoteImportHeaderRepository;
import jp.co.oda32.domain.repository.purchase.TQuoteImportDetailRepository;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.dto.purchase.QuoteImportCreateNewRequest;
import jp.co.oda32.dto.purchase.QuoteImportCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteImportService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_MATCHED = "MATCHED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_CREATED = "CREATED";

    private final TQuoteImportHeaderRepository headerRepository;
    private final TQuoteImportDetailRepository detailRepository;
    private final MGoodsService mGoodsService;
    private final WSalesGoodsService wSalesGoodsService;
    private final MPurchasePriceChangePlanService changePlanService;

    @Transactional(readOnly = true)
    public List<TQuoteImportHeader> findAllHeaders() {
        return headerRepository.findByDelFlgOrderByAddDateTimeDesc(Flag.NO.getValue());
    }

    @Transactional(readOnly = true)
    public TQuoteImportHeader getHeader(Integer importId) {
        return headerRepository.findById(importId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TQuoteImportDetail> getPendingDetails(Integer importId) {
        return detailRepository.findByQuoteImportIdAndStatusOrderByRowNo(importId, STATUS_PENDING);
    }

    @Transactional(readOnly = true)
    public List<TQuoteImportDetail> getProcessedDetails(Integer importId) {
        return detailRepository.findByQuoteImportIdAndStatusNotOrderByRowNo(importId, STATUS_PENDING);
    }

    @Transactional(readOnly = true)
    public int getRemainingCount(Integer importId) {
        return detailRepository.countByQuoteImportIdAndStatus(importId, STATUS_PENDING);
    }

    @Transactional(readOnly = true)
    public Map<Integer, Long> getRemainingCountBatch(List<Integer> importIds) {
        if (importIds.isEmpty()) return Map.of();
        return detailRepository.countByImportIdsAndStatus(importIds, STATUS_PENDING).stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> (Long) row[1]
                ));
    }

    @Transactional
    public TQuoteImportHeader createImport(QuoteImportCreateRequest request) {
        TQuoteImportHeader header = TQuoteImportHeader.builder()
                .shopNo(request.getShopNo())
                .supplierName(request.getSupplierName())
                .fileName(request.getFileName())
                .quoteDate(request.getQuoteDate())
                .effectiveDate(request.getEffectiveDate())
                .changeReason(request.getChangeReason())
                .priceType(request.getPriceType())
                .totalCount(request.getDetails().size())
                .delFlg(Flag.NO.getValue())
                .addDateTime(Timestamp.from(Instant.now()))
                .build();
        TQuoteImportHeader saved = headerRepository.save(header);

        for (QuoteImportCreateRequest.Detail d : request.getDetails()) {
            TQuoteImportDetail detail = TQuoteImportDetail.builder()
                    .quoteImportId(saved.getQuoteImportId())
                    .rowNo(d.getRowNo())
                    .janCode(d.getJanCode())
                    .quoteGoodsName(d.getQuoteGoodsName())
                    .quoteGoodsCode(d.getQuoteGoodsCode())
                    .specification(d.getSpecification())
                    .quantityPerCase(d.getQuantityPerCase())
                    .oldPrice(d.getOldPrice())
                    .newPrice(d.getNewPrice())
                    .oldBoxPrice(d.getOldBoxPrice())
                    .newBoxPrice(d.getNewBoxPrice())
                    .status(STATUS_PENDING)
                    .addDateTime(Timestamp.from(Instant.now()))
                    .build();
            detailRepository.save(detail);
        }
        return saved;
    }

    @Transactional
    public void matchSupplier(Integer importId, String supplierCode, Integer supplierNo) {
        TQuoteImportHeader header = headerRepository.findById(importId).orElseThrow();
        header.setSupplierCode(supplierCode);
        header.setSupplierNo(supplierNo);
        header.setModifyDateTime(Timestamp.from(Instant.now()));
        headerRepository.save(header);
    }

    @Transactional
    public void matchGoods(Integer importId, Integer detailId, String goodsCode, Integer goodsNo) throws Exception {
        TQuoteImportHeader header = headerRepository.findById(importId).orElseThrow();
        TQuoteImportDetail detail = getDetailBelongingToImport(detailId, importId);

        MPurchasePriceChangePlan plan = MPurchasePriceChangePlan.builder()
                .shopNo(header.getShopNo())
                .goodsCode(goodsCode)
                .goodsName(detail.getQuoteGoodsName())
                .janCode(detail.getJanCode())
                .supplierCode(header.getSupplierCode())
                .beforePrice(detail.getOldPrice() != null ? detail.getOldPrice() : java.math.BigDecimal.ZERO)
                .afterPrice(detail.getNewPrice() != null ? detail.getNewPrice() : java.math.BigDecimal.ZERO)
                .changePlanDate(header.getEffectiveDate())
                .changeReason(header.getChangeReason())
                .changeContainNum(detail.getQuantityPerCase() != null ? new java.math.BigDecimal(detail.getQuantityPerCase()) : null)
                .partnerNo(0)
                .destinationNo(0)
                .partnerPriceChangePlanCreated(false)
                .purchasePriceReflect(false)
                .build();
        changePlanService.insert(plan);

        detail.setStatus(STATUS_MATCHED);
        detail.setMatchedGoodsCode(goodsCode);
        detail.setMatchedGoodsNo(goodsNo);
        detail.setProcessedAt(Timestamp.from(Instant.now()));
        detailRepository.save(detail);
    }

    @Transactional
    public void createNewAndMatch(Integer importId, Integer detailId, QuoteImportCreateNewRequest request) throws Exception {
        TQuoteImportHeader header = headerRepository.findById(importId).orElseThrow();
        TQuoteImportDetail detail = getDetailBelongingToImport(detailId, importId);

        MGoods goods = new MGoods();
        goods.setGoodsName(request.getGoods().getGoodsName());
        goods.setJanCode(request.getGoods().getJanCode());
        goods.setMakerNo(request.getGoods().getMakerNo());
        goods.setSpecification(request.getGoods().getSpecification());
        goods.setCaseContainNum(request.getGoods().getCaseContainNum());
        goods.setApplyReducedTaxRate(request.getGoods().isApplyReducedTaxRate());
        MGoods savedGoods = mGoodsService.insert(goods);

        WSalesGoods salesGoods = new WSalesGoods();
        salesGoods.setShopNo(header.getShopNo());
        salesGoods.setGoodsNo(savedGoods.getGoodsNo());
        salesGoods.setGoodsCode(request.getSalesGoods().getGoodsCode());
        salesGoods.setGoodsName(request.getSalesGoods().getGoodsName() != null
                ? request.getSalesGoods().getGoodsName()
                : request.getGoods().getGoodsName());
        salesGoods.setSupplierNo(header.getSupplierNo());
        salesGoods.setPurchasePrice(request.getSalesGoods().getPurchasePrice());
        salesGoods.setGoodsPrice(request.getSalesGoods().getGoodsPrice());
        wSalesGoodsService.insert(salesGoods);

        MPurchasePriceChangePlan plan = MPurchasePriceChangePlan.builder()
                .shopNo(header.getShopNo())
                .goodsCode(request.getSalesGoods().getGoodsCode())
                .goodsName(detail.getQuoteGoodsName())
                .janCode(detail.getJanCode())
                .supplierCode(header.getSupplierCode())
                .beforePrice(detail.getOldPrice() != null ? detail.getOldPrice() : java.math.BigDecimal.ZERO)
                .afterPrice(detail.getNewPrice() != null ? detail.getNewPrice() : java.math.BigDecimal.ZERO)
                .changePlanDate(header.getEffectiveDate())
                .changeReason(header.getChangeReason())
                .partnerNo(0)
                .destinationNo(0)
                .partnerPriceChangePlanCreated(false)
                .purchasePriceReflect(false)
                .build();
        changePlanService.insert(plan);

        detail.setStatus(STATUS_CREATED);
        detail.setMatchedGoodsCode(request.getSalesGoods().getGoodsCode());
        detail.setMatchedGoodsNo(savedGoods.getGoodsNo());
        detail.setProcessedAt(Timestamp.from(Instant.now()));
        detailRepository.save(detail);
    }

    @Transactional
    public void skipDetail(Integer importId, Integer detailId) {
        TQuoteImportDetail detail = getDetailBelongingToImport(detailId, importId);
        detail.setStatus(STATUS_SKIPPED);
        detail.setProcessedAt(Timestamp.from(Instant.now()));
        detailRepository.save(detail);
    }

    @Transactional
    public void undoDetail(Integer importId, Integer detailId) {
        TQuoteImportDetail detail = getDetailBelongingToImport(detailId, importId);
        TQuoteImportHeader header = headerRepository.findById(importId).orElseThrow();

        // 突合/新規作成の場合、対応する仕入価格変更予定を削除
        if (detail.getMatchedGoodsCode() != null) {
            changePlanService.deleteByGoodsCodeAndChangePlanDate(
                    header.getShopNo(), detail.getMatchedGoodsCode(), header.getEffectiveDate());
        }

        // CREATED の場合、新規作成した商品マスタ・販売商品ワークを論理削除
        if (STATUS_CREATED.equals(detail.getStatus()) && detail.getMatchedGoodsNo() != null) {
            cleanupCreatedGoods(header.getShopNo(), detail.getMatchedGoodsNo());
        }

        detail.setStatus(STATUS_PENDING);
        detail.setMatchedGoodsCode(null);
        detail.setMatchedGoodsNo(null);
        detail.setProcessedAt(null);
        detailRepository.save(detail);
    }

    private TQuoteImportDetail getDetailBelongingToImport(Integer detailId, Integer importId) {
        TQuoteImportDetail detail = detailRepository.findById(detailId).orElseThrow();
        if (!importId.equals(detail.getQuoteImportId())) {
            throw new IllegalArgumentException(
                    "明細ID " + detailId + " はインポートID " + importId + " に属していません");
        }
        return detail;
    }

    @Transactional
    public void deleteImport(Integer importId) {
        TQuoteImportHeader header = headerRepository.findById(importId).orElseThrow();

        // 処理済み明細に対応するデータを削除
        List<TQuoteImportDetail> processedDetails = detailRepository.findByQuoteImportIdAndStatusNotOrderByRowNo(importId, STATUS_PENDING);
        for (TQuoteImportDetail detail : processedDetails) {
            // 仕入価格変更予定を削除
            if (detail.getMatchedGoodsCode() != null) {
                changePlanService.deleteByGoodsCodeAndChangePlanDate(
                        header.getShopNo(), detail.getMatchedGoodsCode(), header.getEffectiveDate());
            }
            // CREATED の場合、新規作成した商品マスタ・販売商品ワークを論理削除
            if (STATUS_CREATED.equals(detail.getStatus()) && detail.getMatchedGoodsNo() != null) {
                cleanupCreatedGoods(header.getShopNo(), detail.getMatchedGoodsNo());
            }
        }

        detailRepository.deleteByQuoteImportId(importId);
        header.setDelFlg(Flag.YES.getValue());
        header.setModifyDateTime(Timestamp.from(Instant.now()));
        headerRepository.save(header);
    }

    /**
     * 見積取込で新規作成した商品マスタ・販売商品ワークを論理削除します。
     */
    private void cleanupCreatedGoods(Integer shopNo, Integer goodsNo) {
        try {
            WSalesGoods wsg = wSalesGoodsService.getByPK(shopNo, goodsNo);
            if (wsg != null) {
                wsg.setDelFlg(Flag.YES.getValue());
                wSalesGoodsService.update(wsg);
            }
            MGoods goods = mGoodsService.getByGoodsNo(goodsNo);
            if (goods != null) {
                goods.setDelFlg(Flag.YES.getValue());
                mGoodsService.update(goods);
            }
        } catch (Exception e) {
            // クリーンアップ失敗は主処理を止めない
            log.warn("クリーンアップ失敗: {}", e.getMessage(), e);
        }
    }
}
