package jp.co.oda32.api.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MSalesGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.dto.goods.SalesGoodsCreateRequest;
import jp.co.oda32.dto.goods.SalesGoodsDetailResponse;
import jp.co.oda32.dto.goods.SalesGoodsUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sales-goods")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SalesGoodsController {

    private final WSalesGoodsService wSalesGoodsService;
    private final MSalesGoodsService mSalesGoodsService;
    private final MGoodsService mGoodsService;
    private final MSupplierService mSupplierService;

    /**
     * 販売商品ワーク一覧。
     * paymentSupplierNo が指定された場合は、紐づく全 m_supplier をグループ展開して
     * supplier_no IN (...) でフィルタする。**この場合 supplierNo パラメータは無視される。**
     */
    @GetMapping("/work")
    public ResponseEntity<Page<SalesGoodsDetailResponse>> listWork(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer supplierNo,
            @RequestParam(required = false) Integer paymentSupplierNo,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<WSalesGoods> page;
        if (paymentSupplierNo != null) {
            List<MSupplier> siblings = mSupplierService.findByPaymentSupplierNo(shopNo, paymentSupplierNo);
            if (siblings.isEmpty()) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
            Set<Integer> siblingNos = siblings.stream()
                    .map(MSupplier::getSupplierNo)
                    .collect(Collectors.toSet());
            page = wSalesGoodsService.findBySupplierNoListPaged(shopNo, goodsName, goodsCode, siblingNos, Flag.NO, pageable);
        } else {
            page = wSalesGoodsService.findPaged(shopNo, null, goodsName, null, goodsCode, keyword, supplierNo, Flag.NO, pageable);
        }
        return ResponseEntity.ok(page.map(SalesGoodsDetailResponse::from));
    }

    /**
     * 販売商品マスタ一覧。
     * paymentSupplierNo が指定された場合は、紐づく全 m_supplier をグループ展開して
     * supplier_no IN (...) でフィルタする。**この場合 supplierNo パラメータは無視される。**
     */
    @GetMapping("/master")
    public ResponseEntity<Page<SalesGoodsDetailResponse>> listMaster(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer supplierNo,
            @RequestParam(required = false) Integer paymentSupplierNo,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<MSalesGoods> page;
        if (paymentSupplierNo != null) {
            List<MSupplier> siblings = mSupplierService.findByPaymentSupplierNo(shopNo, paymentSupplierNo);
            if (siblings.isEmpty()) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
            Set<Integer> siblingNos = siblings.stream()
                    .map(MSupplier::getSupplierNo)
                    .collect(Collectors.toSet());
            page = mSalesGoodsService.findBySupplierNoListPaged(shopNo, goodsName, goodsCode, siblingNos, Flag.NO, pageable);
        } else {
            page = mSalesGoodsService.findPaged(shopNo, null, goodsName, goodsCode, keyword, supplierNo, Flag.NO, pageable);
        }
        return ResponseEntity.ok(page.map(SalesGoodsDetailResponse::from));
    }

    @GetMapping("/work/{shopNo}/{goodsNo}")
    public ResponseEntity<SalesGoodsDetailResponse> getWork(
            @PathVariable Integer shopNo,
            @PathVariable Integer goodsNo) {
        WSalesGoods work = wSalesGoodsService.getByPK(shopNo, goodsNo);
        if (work == null) {
            return ResponseEntity.notFound().build();
        }
        MSalesGoods master = mSalesGoodsService.getByPK(shopNo, goodsNo);
        boolean hasMaster = master != null && Flag.NO.getValue().equals(master.getDelFlg());
        return ResponseEntity.ok(SalesGoodsDetailResponse.from(work, hasMaster));
    }

    @GetMapping("/master/{shopNo}/{goodsNo}")
    public ResponseEntity<SalesGoodsDetailResponse> getMaster(
            @PathVariable Integer shopNo,
            @PathVariable Integer goodsNo) {
        MSalesGoods master = mSalesGoodsService.getByPK(shopNo, goodsNo);
        if (master == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(SalesGoodsDetailResponse.from(master));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/work")
    public ResponseEntity<SalesGoodsDetailResponse> createWork(
            @Valid @RequestBody SalesGoodsCreateRequest request) throws Exception {
        WSalesGoods work = new WSalesGoods();
        work.setShopNo(request.getShopNo());
        work.setGoodsNo(request.getGoodsNo());
        work.setGoodsCode(request.getGoodsCode());
        work.setGoodsSkuCode(request.getGoodsSkuCode());
        work.setGoodsName(request.getGoodsName());
        work.setKeyword(request.getKeyword());
        work.setSupplierNo(request.getSupplierNo());
        work.setReferencePrice(request.getReferencePrice());
        work.setPurchasePrice(request.getPurchasePrice());
        work.setGoodsPrice(request.getGoodsPrice());
        work.setCatchphrase(request.getCatchphrase());
        work.setGoodsIntroduction(request.getGoodsIntroduction());
        work.setGoodsDescription1(request.getGoodsDescription1());
        work.setGoodsDescription2(request.getGoodsDescription2());
        WSalesGoods saved = wSalesGoodsService.insert(work);
        return ResponseEntity.ok(SalesGoodsDetailResponse.from(saved));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/work/{shopNo}/{goodsNo}")
    public ResponseEntity<SalesGoodsDetailResponse> updateWork(
            @PathVariable Integer shopNo,
            @PathVariable Integer goodsNo,
            @Valid @RequestBody SalesGoodsUpdateRequest request) throws Exception {
        WSalesGoods work = wSalesGoodsService.getByPK(shopNo, goodsNo);
        if (work == null) {
            return ResponseEntity.notFound().build();
        }
        work.setGoodsCode(request.getGoodsCode());
        work.setGoodsSkuCode(request.getGoodsSkuCode());
        work.setGoodsName(request.getGoodsName());
        work.setKeyword(request.getKeyword());
        work.setSupplierNo(request.getSupplierNo());
        work.setReferencePrice(request.getReferencePrice());
        work.setPurchasePrice(request.getPurchasePrice());
        work.setGoodsPrice(request.getGoodsPrice());
        work.setCatchphrase(request.getCatchphrase());
        work.setGoodsIntroduction(request.getGoodsIntroduction());
        work.setGoodsDescription1(request.getGoodsDescription1());
        work.setGoodsDescription2(request.getGoodsDescription2());
        work.setShopNo(shopNo);
        work.setGoodsNo(goodsNo);
        WSalesGoods saved = wSalesGoodsService.update(work);
        return ResponseEntity.ok(SalesGoodsDetailResponse.from(saved));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/master/{shopNo}/{goodsNo}")
    public ResponseEntity<SalesGoodsDetailResponse> updateMaster(
            @PathVariable Integer shopNo,
            @PathVariable Integer goodsNo,
            @Valid @RequestBody SalesGoodsUpdateRequest request) throws Exception {
        MSalesGoods master = mSalesGoodsService.getByPK(shopNo, goodsNo);
        if (master == null) {
            return ResponseEntity.notFound().build();
        }
        master.setGoodsCode(request.getGoodsCode());
        master.setGoodsSkuCode(request.getGoodsSkuCode());
        master.setGoodsName(request.getGoodsName());
        master.setKeyword(request.getKeyword());
        master.setSupplierNo(request.getSupplierNo());
        master.setReferencePrice(request.getReferencePrice());
        master.setPurchasePrice(request.getPurchasePrice());
        master.setGoodsPrice(request.getGoodsPrice());
        master.setCatchphrase(request.getCatchphrase());
        master.setGoodsIntroduction(request.getGoodsIntroduction());
        master.setGoodsDescription1(request.getGoodsDescription1());
        master.setGoodsDescription2(request.getGoodsDescription2());
        master.setShopNo(shopNo);
        master.setGoodsNo(goodsNo);
        MSalesGoods saved = mSalesGoodsService.update(master);
        return ResponseEntity.ok(SalesGoodsDetailResponse.from(saved));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/work/{shopNo}/{goodsNo}/reflect")
    public ResponseEntity<SalesGoodsDetailResponse> reflectToMaster(
            @PathVariable Integer shopNo,
            @PathVariable Integer goodsNo) throws Exception {
        MSalesGoods saved = mSalesGoodsService.reflectFromWork(shopNo, goodsNo);
        if (saved == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(SalesGoodsDetailResponse.from(saved));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/work/{shopNo}/{goodsNo}")
    public ResponseEntity<Void> deleteWork(
            @PathVariable Integer shopNo,
            @PathVariable Integer goodsNo,
            @RequestParam(defaultValue = "false") boolean deleteMaster) throws Exception {
        WSalesGoods work = wSalesGoodsService.getByPK(shopNo, goodsNo);
        if (work == null || Flag.YES.getValue().equals(work.getDelFlg())) {
            return ResponseEntity.notFound().build();
        }
        work.setDelFlg(Flag.YES.getValue());
        wSalesGoodsService.update(work);

        if (deleteMaster) {
            MSalesGoods master = mSalesGoodsService.getByPK(shopNo, goodsNo);
            if (master != null && Flag.NO.getValue().equals(master.getDelFlg())) {
                master.setDelFlg(Flag.YES.getValue());
                mSalesGoodsService.update(master);
            }
        }
        return ResponseEntity.noContent().build();
    }
}
