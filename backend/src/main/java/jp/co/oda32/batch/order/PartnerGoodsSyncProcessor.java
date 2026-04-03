package jp.co.oda32.batch.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.embeddable.MPartnerGoodsPK;
import jp.co.oda32.domain.model.goods.MPartnerGoods;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.service.goods.MPartnerGoodsService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 得意先商品同期の得意先単位処理。
 * Tasklet から呼び出されるため、@Transactional(REQUIRES_NEW) がプロキシ経由で有効になる。
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class PartnerGoodsSyncProcessor {

    private final TOrderDetailService orderDetailService;
    private final MPartnerGoodsService partnerGoodsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] partnerOrderProcess(Integer shopNo, Integer companyNo, Integer partnerNo) {
        List<TOrderDetail> orderDetailList = this.orderDetailService.find(
                shopNo, companyNo, null, null, null, null, null, null, null,
                LocalDateTime.now().minusYears(1), LocalDateTime.now(),
                null, null, Flag.NO);

        List<TOrderDetail> validOrderDetails = orderDetailList.stream()
                .filter(od -> od.getTOrder() != null && od.getTOrder().getDestinationNo() != null)
                .collect(Collectors.toList());

        Map<String, List<TOrderDetail>> groupedOrders = validOrderDetails.stream()
                .collect(Collectors.groupingBy(od -> od.getGoodsNo() + "_" + od.getTOrder().getDestinationNo()));

        int insertCount = 0;
        int updateCount = 0;

        for (Map.Entry<String, List<TOrderDetail>> entry : groupedOrders.entrySet()) {
            String[] ids = entry.getKey().split("_");
            int goodsNo = Integer.parseInt(ids[0]);
            int destinationNo = Integer.parseInt(ids[1]);
            List<TOrderDetail> details = entry.getValue();

            BigDecimal orderNum = details.stream()
                    .map(od -> od.getOrderNum().subtract(od.getCancelNum()).subtract(od.getReturnNum()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            LocalDate lastSalesDate = details.stream()
                    .filter(od -> od.getTOrder().getOrderDateTime() != null)
                    .map(od -> od.getTOrder().getOrderDateTime().toLocalDate())
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            TOrderDetail representative = details.stream()
                    .filter(od -> od.getTOrder().getOrderDateTime() != null)
                    .max(Comparator.comparing(od -> od.getTOrder().getOrderDateTime()))
                    .orElse(details.get(0));

            boolean inserted = syncPartnerGoods(shopNo, companyNo, partnerNo, goodsNo, destinationNo,
                    orderNum, lastSalesDate, representative);
            if (inserted) {
                insertCount++;
            } else {
                updateCount++;
            }
        }

        return new int[]{insertCount, updateCount};
    }

    private boolean syncPartnerGoods(Integer shopNo, Integer companyNo, Integer partnerNo,
                                     Integer goodsNo, Integer destinationNo,
                                     BigDecimal orderNum, LocalDate lastSalesDate,
                                     TOrderDetail representative) {
        MPartnerGoods existing = this.partnerGoodsService.getByPK(
                MPartnerGoodsPK.builder().partnerNo(partnerNo).goodsNo(goodsNo).destinationNo(destinationNo).build());

        try {
            if (existing == null) {
                MPartnerGoods newGoods = MPartnerGoods.builder()
                        .partnerNo(partnerNo)
                        .goodsNo(goodsNo)
                        .destinationNo(destinationNo)
                        .shopNo(shopNo)
                        .companyNo(companyNo)
                        .goodsCode(representative.getGoodsCode())
                        .goodsName(representative.getGoodsName())
                        .goodsPrice(representative.getGoodsPrice())
                        .orderNumPerYear(orderNum)
                        .lastSalesDate(lastSalesDate)
                        .build();
                this.partnerGoodsService.insert(newGoods);
                log.info("得意先商品を新規登録 partnerNo:{}, goodsNo:{}, destinationNo:{}, goodsName:{}",
                        partnerNo, goodsNo, destinationNo, representative.getGoodsName());
                return true;
            } else {
                existing.setOrderNumPerYear(orderNum);
                if (lastSalesDate != null && (existing.getLastSalesDate() == null || lastSalesDate.isAfter(existing.getLastSalesDate()))) {
                    existing.setLastSalesDate(lastSalesDate);
                }
                this.partnerGoodsService.update(existing);
                return false;
            }
        } catch (Exception e) {
            log.error("得意先商品の同期に失敗しました。partnerNo:{}, goodsNo:{}, destinationNo:{}, 原因:{}",
                    partnerNo, goodsNo, destinationNo, e.getMessage());
            return false;
        }
    }
}
