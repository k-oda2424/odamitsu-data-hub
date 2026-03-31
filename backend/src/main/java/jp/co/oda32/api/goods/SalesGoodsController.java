package jp.co.oda32.api.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MSalesGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.dto.goods.SalesGoodsCreateRequest;
import jp.co.oda32.dto.goods.SalesGoodsDetailResponse;
import jp.co.oda32.dto.goods.SalesGoodsUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sales-goods")
@RequiredArgsConstructor
public class SalesGoodsController {

    private final WSalesGoodsService wSalesGoodsService;
    private final MSalesGoodsService mSalesGoodsService;
    private final MGoodsService mGoodsService;

    @GetMapping("/work")
    public ResponseEntity<List<SalesGoodsDetailResponse>> listWork(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer supplierNo) {
        List<WSalesGoods> list = wSalesGoodsService.find(shopNo, null, goodsName, null, goodsCode, keyword, supplierNo, Flag.NO);
        return ResponseEntity.ok(list.stream().map(SalesGoodsDetailResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/master")
    public ResponseEntity<List<SalesGoodsDetailResponse>> listMaster(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer supplierNo) {
        List<MSalesGoods> list = mSalesGoodsService.find(shopNo, null, goodsName, goodsCode, keyword, supplierNo, Flag.NO);
        return ResponseEntity.ok(list.stream().map(SalesGoodsDetailResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/work/{shopNo}/{goodsNo}")
    public ResponseEntity<SalesGoodsDetailResponse> getWork(
            @PathVariable Integer shopNo,
            @PathVariable Integer goodsNo) {
        WSalesGoods work = wSalesGoodsService.getByPK(shopNo, goodsNo);
        if (work == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(SalesGoodsDetailResponse.from(work));
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

    @DeleteMapping("/work/{shopNo}/{goodsNo}")
    public ResponseEntity<Void> deleteWork(
            @PathVariable Integer shopNo,
            @PathVariable Integer goodsNo) throws Exception {
        WSalesGoods work = wSalesGoodsService.getByPK(shopNo, goodsNo);
        if (work == null) {
            return ResponseEntity.notFound().build();
        }
        work.setDelFlg(Flag.YES.getValue());
        wSalesGoodsService.update(work);
        return ResponseEntity.noContent().build();
    }
}
