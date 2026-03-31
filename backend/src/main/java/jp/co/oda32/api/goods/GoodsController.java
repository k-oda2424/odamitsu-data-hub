package jp.co.oda32.api.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.service.goods.CommonSalesGoodsService;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MSalesGoodsService;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.dto.goods.GoodsCreateRequest;
import jp.co.oda32.dto.goods.GoodsDetailResponse;
import jp.co.oda32.dto.goods.GoodsResponse;
import jp.co.oda32.dto.goods.SalesGoodsCreateRequest;
import jp.co.oda32.dto.goods.SalesGoodsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final MGoodsService mGoodsService;
    private final MSalesGoodsService mSalesGoodsService;
    private final WSalesGoodsService wSalesGoodsService;
    private final CommonSalesGoodsService commonSalesGoodsService;

    @GetMapping
    public ResponseEntity<List<GoodsResponse>> list(
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String janCode,
            @RequestParam(required = false) Integer makerNo) {
        List<MGoods> goods = mGoodsService.find(null, goodsName, keyword, janCode, makerNo, Flag.NO);
        return ResponseEntity.ok(goods.stream().map(GoodsResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/{goodsNo}")
    public ResponseEntity<GoodsResponse> get(@PathVariable Integer goodsNo) {
        MGoods goods = mGoodsService.getByGoodsNo(goodsNo);
        if (goods == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(GoodsResponse.from(goods));
    }

    @GetMapping("/{goodsNo}/detail")
    public ResponseEntity<GoodsDetailResponse> getDetail(
            @PathVariable Integer goodsNo,
            @RequestParam(required = false) Integer shopNo) {
        MGoods goods = mGoodsService.getByGoodsNo(goodsNo);
        if (goods == null) {
            return ResponseEntity.notFound().build();
        }
        List<MSalesGoods> masterList = shopNo != null
                ? mSalesGoodsService.find(shopNo, goodsNo, null, null, null, null, Flag.NO)
                : List.of();
        List<WSalesGoods> workList = shopNo != null
                ? wSalesGoodsService.find(shopNo, goodsNo, null, null, null, null, null, Flag.NO)
                : List.of();
        return ResponseEntity.ok(GoodsDetailResponse.from(goods, masterList, workList));
    }

    @GetMapping("/available-for-sales")
    public ResponseEntity<List<GoodsResponse>> listAvailableForSales(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String janCode,
            @RequestParam(required = false) Integer makerNo) {
        List<MGoods> goods = mGoodsService.findByNotExistWSalesGoods(goodsName, keyword, janCode, makerNo);
        return ResponseEntity.ok(goods.stream().map(GoodsResponse::from).collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<GoodsResponse> create(@Valid @RequestBody GoodsCreateRequest request) throws Exception {
        MGoods goods = new MGoods();
        goods.setGoodsName(request.getGoodsName());
        goods.setJanCode(request.getJanCode());
        goods.setMakerNo(request.getMakerNo());
        goods.setKeyword(request.getKeyword());
        goods.setTaxCategory(request.getTaxCategory());
        goods.setSpecification(request.getSpecification());
        goods.setCaseContainNum(request.getCaseContainNum());
        goods.setApplyReducedTaxRate(request.isApplyReducedTaxRate());
        MGoods saved = mGoodsService.insert(goods);
        return ResponseEntity.ok(GoodsResponse.from(saved));
    }

    @PutMapping("/{goodsNo}")
    public ResponseEntity<GoodsResponse> update(@PathVariable Integer goodsNo, @Valid @RequestBody GoodsCreateRequest request) throws Exception {
        MGoods goods = mGoodsService.getByGoodsNo(goodsNo);
        if (goods == null) {
            return ResponseEntity.notFound().build();
        }
        goods.setGoodsName(request.getGoodsName());
        goods.setJanCode(request.getJanCode());
        goods.setMakerNo(request.getMakerNo());
        goods.setKeyword(request.getKeyword());
        goods.setTaxCategory(request.getTaxCategory());
        goods.setSpecification(request.getSpecification());
        goods.setCaseContainNum(request.getCaseContainNum());
        goods.setApplyReducedTaxRate(request.isApplyReducedTaxRate());
        MGoods saved = mGoodsService.update(goods);
        return ResponseEntity.ok(GoodsResponse.from(saved));
    }

    @PostMapping("/{goodsNo}/sales-goods")
    public ResponseEntity<SalesGoodsResponse> createSalesGoods(
            @PathVariable Integer goodsNo,
            @Valid @RequestBody SalesGoodsCreateRequest request) throws Exception {
        MSalesGoods mSalesGoods = new MSalesGoods();
        BeanUtils.copyProperties(request, mSalesGoods);
        mSalesGoods.setGoodsNo(goodsNo);
        MSalesGoods saved = mSalesGoodsService.save(mSalesGoods);
        return ResponseEntity.ok(SalesGoodsResponse.from(saved));
    }

    @DeleteMapping("/{goodsNo}")
    public ResponseEntity<Void> delete(@PathVariable Integer goodsNo) throws Exception {
        MGoods goods = mGoodsService.getByGoodsNo(goodsNo);
        if (goods == null) {
            return ResponseEntity.notFound().build();
        }
        goods.setDelFlg(Flag.YES.getValue());
        mGoodsService.update(goods);
        return ResponseEntity.noContent().build();
    }
}
