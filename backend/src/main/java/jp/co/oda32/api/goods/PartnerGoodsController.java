package jp.co.oda32.api.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.embeddable.MPartnerGoodsPK;
import jp.co.oda32.domain.model.goods.MPartnerGoods;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.model.order.TReturnDetail;
import jp.co.oda32.domain.service.goods.MPartnerGoodsService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import jp.co.oda32.domain.service.order.TReturnDetailService;
import jp.co.oda32.dto.goods.OrderHistoryResponse;
import jp.co.oda32.dto.goods.PartnerGoodsDetailResponse;
import jp.co.oda32.dto.goods.PartnerGoodsResponse;
import jp.co.oda32.dto.goods.PartnerGoodsUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/partner-goods")
@RequiredArgsConstructor
public class PartnerGoodsController {

    private final MPartnerGoodsService mPartnerGoodsService;
    private final TOrderDetailService tOrderDetailService;
    private final TReturnDetailService tReturnDetailService;

    @GetMapping
    public ResponseEntity<List<PartnerGoodsResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer companyNo,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer destinationNo) {
        List<MPartnerGoods> partnerGoodsList = mPartnerGoodsService.find(
                shopNo, companyNo, partnerCode, null, goodsName, goodsCode, keyword, destinationNo, Flag.NO);
        partnerGoodsList.sort(Comparator.comparing(
                MPartnerGoods::getLastSalesDate,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return ResponseEntity.ok(partnerGoodsList.stream()
                .map(PartnerGoodsResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{partnerNo}/{destinationNo}/{goodsNo}")
    public ResponseEntity<PartnerGoodsDetailResponse> getDetail(
            @PathVariable Integer partnerNo,
            @PathVariable Integer destinationNo,
            @PathVariable Integer goodsNo) {
        MPartnerGoods pg = mPartnerGoodsService.getByPK(
                MPartnerGoodsPK.builder()
                        .partnerNo(partnerNo)
                        .goodsNo(goodsNo)
                        .destinationNo(destinationNo)
                        .build());
        if (pg == null) {
            return ResponseEntity.notFound().build();
        }
        List<OrderHistoryResponse> orderHistory = getOrderHistory(pg.getShopNo(), pg.getCompanyNo(), goodsNo);
        return ResponseEntity.ok(PartnerGoodsDetailResponse.from(pg, orderHistory));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{partnerNo}/{destinationNo}/{goodsNo}")
    public ResponseEntity<PartnerGoodsResponse> update(
            @PathVariable Integer partnerNo,
            @PathVariable Integer destinationNo,
            @PathVariable Integer goodsNo,
            @Valid @RequestBody PartnerGoodsUpdateRequest request) throws Exception {
        MPartnerGoods pg = mPartnerGoodsService.getByPK(
                MPartnerGoodsPK.builder()
                        .partnerNo(partnerNo)
                        .goodsNo(goodsNo)
                        .destinationNo(destinationNo)
                        .build());
        if (pg == null) {
            return ResponseEntity.notFound().build();
        }
        pg.setGoodsName(request.getGoodsName());
        pg.setKeyword(request.getKeyword());
        if (request.getGoodsPrice() != null) {
            pg.setGoodsPrice(request.getGoodsPrice());
        }
        MPartnerGoods saved = mPartnerGoodsService.update(pg);
        return ResponseEntity.ok(PartnerGoodsResponse.from(saved));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{partnerNo}/{destinationNo}/{goodsNo}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer partnerNo,
            @PathVariable Integer destinationNo,
            @PathVariable Integer goodsNo) throws Exception {
        MPartnerGoods pg = mPartnerGoodsService.getByPK(
                MPartnerGoodsPK.builder()
                        .partnerNo(partnerNo)
                        .goodsNo(goodsNo)
                        .destinationNo(destinationNo)
                        .build());
        if (pg == null) {
            return ResponseEntity.notFound().build();
        }
        pg.setDelFlg(Flag.YES.getValue());
        mPartnerGoodsService.update(pg);
        return ResponseEntity.noContent().build();
    }

    private List<OrderHistoryResponse> getOrderHistory(int shopNo, int companyNo, int goodsNo) {
        List<TOrderDetail> orderDetails = tOrderDetailService.find(
                shopNo, companyNo, null, null, null, null, goodsNo,
                null, null, null, null, null, null, Flag.NO);
        List<TReturnDetail> returnDetails = tReturnDetailService.find(
                shopNo, companyNo, null, null, null, goodsNo,
                null, null, null, null, Flag.NO);

        List<OrderHistoryResponse> history = new ArrayList<>();
        orderDetails.stream()
                .map(OrderHistoryResponse::from)
                .forEach(history::add);
        returnDetails.stream()
                .map(rd -> {
                    OrderHistoryResponse h = OrderHistoryResponse.from(rd);
                    if (h.getGoodsNum() != null) {
                        h.setGoodsNum(h.getGoodsNum().negate());
                    }
                    return h;
                })
                .forEach(history::add);

        history.sort(Comparator.comparing(OrderHistoryResponse::getOrderDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        if (history.size() > 100) {
            return history.subList(0, 100);
        }
        return history;
    }
}
