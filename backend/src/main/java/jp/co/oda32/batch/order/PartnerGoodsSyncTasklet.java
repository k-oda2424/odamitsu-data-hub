package jp.co.oda32.batch.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.embeddable.MPartnerGoodsPK;
import jp.co.oda32.domain.model.goods.MPartnerGoods;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.service.goods.MPartnerGoodsService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 得意先商品マスタ同期バッチ
 * 注文明細から得意先商品マスタの登録・更新を行う。
 * - 未登録の得意先商品は新規INSERT
 * - 既存の得意先商品は年間注文数量・最終売上日をUPDATE
 *
 * @author k_oda
 * @since 2019/04/11
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class PartnerGoodsSyncTasklet implements Tasklet {
    @NonNull
    private MPartnerService partnerService;
    @NonNull
    private TOrderDetailService orderDetailService;
    @NonNull
    private MPartnerGoodsService partnerGoodsService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 全ての年間注文数量を0にする
        int cnt = this.partnerGoodsService.updateAllClearOrderNumPerYear();
        log.info("全年間注文数量を0に更新しました。更新件数:{}", cnt);

        // 過去1年以内に注文がある得意先を検索
        List<MPartner> partnerList = this.partnerService.find(null, null, null, LocalDate.now().minusYears(1), null, Flag.NO);

        // 得意先毎に処理
        partnerList.sort(Comparator.comparing(MPartner::getCompanyNo));
        int insertCount = 0;
        int updateCount = 0;
        for (MPartner partner : partnerList) {
            int[] result = partnerOrderProcess(partner.getShopNo(), partner.getCompanyNo(), partner.getPartnerNo());
            insertCount += result[0];
            updateCount += result[1];
        }
        log.info("得意先商品同期完了。新規登録:{}件, 更新:{}件", insertCount, updateCount);

        return RepeatStatus.FINISHED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] partnerOrderProcess(Integer shopNo, Integer companyNo, Integer partnerNo) {
        // 過去1年間の注文明細を検索（delFlg=NOのみ）
        List<TOrderDetail> orderDetailList = this.orderDetailService.find(
                shopNo, companyNo, null, null, null, null, null, null, null,
                LocalDateTime.now().minusYears(1), LocalDateTime.now(),
                null, null, Flag.NO);

        // tOrderがnullまたはdestinationNoがnullのレコードを除外
        List<TOrderDetail> validOrderDetails = orderDetailList.stream()
                .filter(od -> od.getTOrder() != null && od.getTOrder().getDestinationNo() != null)
                .collect(Collectors.toList());

        // 商品番号_届け先番号でグルーピング
        Map<String, List<TOrderDetail>> groupedOrders = validOrderDetails.stream()
                .collect(Collectors.groupingBy(od -> od.getGoodsNo() + "_" + od.getTOrder().getDestinationNo()));

        int insertCount = 0;
        int updateCount = 0;

        for (Map.Entry<String, List<TOrderDetail>> entry : groupedOrders.entrySet()) {
            String[] ids = entry.getKey().split("_");
            int goodsNo = Integer.parseInt(ids[0]);
            int destinationNo = Integer.parseInt(ids[1]);
            List<TOrderDetail> details = entry.getValue();

            // 年間注文数量
            BigDecimal orderNum = details.stream()
                    .map(od -> od.getOrderNum().subtract(od.getCancelNum()).subtract(od.getReturnNum()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 最終売上日
            LocalDate lastSalesDate = details.stream()
                    .filter(od -> od.getTOrder().getOrderDateTime() != null)
                    .map(od -> od.getTOrder().getOrderDateTime().toLocalDate())
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            // 代表の注文明細（商品コード・商品名・売価の取得用）
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

    /**
     * 得意先商品の登録または更新
     *
     * @return true=新規登録, false=更新
     */
    private boolean syncPartnerGoods(Integer shopNo, Integer companyNo, Integer partnerNo,
                                     Integer goodsNo, Integer destinationNo,
                                     BigDecimal orderNum, LocalDate lastSalesDate,
                                     TOrderDetail representative) {
        MPartnerGoods existing = this.partnerGoodsService.getByPK(
                MPartnerGoodsPK.builder().partnerNo(partnerNo).goodsNo(goodsNo).destinationNo(destinationNo).build());

        try {
            if (existing == null) {
                // 新規登録
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
                // 更新
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
