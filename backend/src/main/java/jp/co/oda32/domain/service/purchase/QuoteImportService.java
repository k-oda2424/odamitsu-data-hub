package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.model.purchase.TQuoteImportHeader;
import jp.co.oda32.domain.model.purchase.WQuoteImportDetail;
import jp.co.oda32.domain.repository.purchase.TQuoteImportHeaderRepository;
import jp.co.oda32.domain.repository.purchase.WQuoteImportDetailRepository;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.dto.purchase.QuoteImportCreateNewRequest;
import jp.co.oda32.dto.purchase.QuoteImportCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuoteImportService {

    private final TQuoteImportHeaderRepository headerRepository;
    private final WQuoteImportDetailRepository detailRepository;
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
    public List<WQuoteImportDetail> getDetails(Integer importId) {
        return detailRepository.findByQuoteImportIdOrderByRowNo(importId);
    }

    @Transactional(readOnly = true)
    public int getRemainingCount(Integer importId) {
        return detailRepository.countByQuoteImportId(importId);
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
            WQuoteImportDetail detail = WQuoteImportDetail.builder()
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
        WQuoteImportDetail detail = detailRepository.findById(detailId).orElseThrow();

        MPurchasePriceChangePlan plan = MPurchasePriceChangePlan.builder()
                .shopNo(header.getShopNo())
                .goodsCode(goodsCode)
                .goodsName(detail.getQuoteGoodsName())
                .janCode(detail.getJanCode())
                .supplierCode(header.getSupplierCode())
                .beforePrice(detail.getOldPrice())
                .afterPrice(detail.getNewPrice())
                .changePlanDate(header.getEffectiveDate())
                .changeReason(header.getChangeReason())
                .changeContainNum(detail.getQuantityPerCase() != null ? new java.math.BigDecimal(detail.getQuantityPerCase()) : null)
                .partnerNo(0)
                .destinationNo(0)
                .partnerPriceChangePlanCreated(false)
                .purchasePriceReflect(false)
                .build();
        changePlanService.insert(plan);

        detailRepository.deleteById(detailId);
    }

    @Transactional
    public void createNewAndMatch(Integer importId, Integer detailId, QuoteImportCreateNewRequest request) throws Exception {
        TQuoteImportHeader header = headerRepository.findById(importId).orElseThrow();
        WQuoteImportDetail detail = detailRepository.findById(detailId).orElseThrow();

        // 1. MGoods 作成
        MGoods goods = new MGoods();
        goods.setGoodsName(request.getGoods().getGoodsName());
        goods.setJanCode(request.getGoods().getJanCode());
        goods.setMakerNo(request.getGoods().getMakerNo());
        goods.setSpecification(request.getGoods().getSpecification());
        goods.setCaseContainNum(request.getGoods().getCaseContainNum());
        goods.setApplyReducedTaxRate(request.getGoods().isApplyReducedTaxRate());
        MGoods savedGoods = mGoodsService.insert(goods);

        // 2. WSalesGoods 作成
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

        // 3. MPurchasePriceChangePlan 作成
        MPurchasePriceChangePlan plan = MPurchasePriceChangePlan.builder()
                .shopNo(header.getShopNo())
                .goodsCode(request.getSalesGoods().getGoodsCode())
                .goodsName(detail.getQuoteGoodsName())
                .janCode(detail.getJanCode())
                .supplierCode(header.getSupplierCode())
                .beforePrice(detail.getOldPrice())
                .afterPrice(detail.getNewPrice())
                .changePlanDate(header.getEffectiveDate())
                .changeReason(header.getChangeReason())
                .partnerNo(0)
                .destinationNo(0)
                .partnerPriceChangePlanCreated(false)
                .purchasePriceReflect(false)
                .build();
        changePlanService.insert(plan);

        // 4. ワーク行削除
        detailRepository.deleteById(detailId);
    }

    @Transactional
    public void skipDetail(Integer detailId) {
        detailRepository.deleteById(detailId);
    }

    @Transactional
    public void deleteImport(Integer importId) {
        detailRepository.deleteByQuoteImportId(importId);
        TQuoteImportHeader header = headerRepository.findById(importId).orElseThrow();
        header.setDelFlg(Flag.YES.getValue());
        header.setModifyDateTime(Timestamp.from(Instant.now()));
        headerRepository.save(header);
    }
}
