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
 * 年間注文数カウント・最終売上日更新バッチ
 *
 * @author k_oda
 * @since 2019/04/11
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class OrderNumCountTasklet implements Tasklet {
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
        log.info(String.format("全年間注文数量を0に更新しました。更新件数:%d", cnt));

        // 過去1年以内に注文がある得意先を検索
        List<MPartner> partnerList = this.partnerService.find(null, null, null, LocalDate.now().minusYears(1), null, Flag.NO);

        // 得意先毎に処理
        partnerList.sort(Comparator.comparing(MPartner::getCompanyNo));
        partnerList.forEach(partner -> partnerOrderProcess(partner.getShopNo(), partner.getCompanyNo(), partner.getPartnerNo()));

        return RepeatStatus.FINISHED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void partnerOrderProcess(Integer shopNo, Integer companyNo, Integer partnerNo) {
        // 過去1年間の注文明細を検索（delFlg=NOのみ）
        List<TOrderDetail> orderDetailList = this.orderDetailService.find(
                shopNo,
                companyNo,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now().minusYears(1),
                LocalDateTime.now(),
                null,
                null,
                Flag.NO
        );

        // tOrderがnullまたはdestinationNoがnullのレコードを除外
        List<TOrderDetail> validOrderDetails = orderDetailList.stream()
                .filter(od -> od.getTOrder() != null && od.getTOrder().getDestinationNo() != null)
                .collect(Collectors.toList());

        // 商品番号_届け先番号でグルーピング
        // 年間注文数量
        Map<String, BigDecimal> orderNumMap = validOrderDetails.stream()
                .collect(Collectors.groupingBy(
                        od -> od.getGoodsNo() + "_" + od.getTOrder().getDestinationNo(),
                        Collectors.reducing(BigDecimal.ZERO,
                                od -> od.getOrderNum().subtract(od.getCancelNum()).subtract(od.getReturnNum()),
                                BigDecimal::add)));

        // 最終売上日（過去1年間のデータから最新の注文日を取得）
        Map<String, LocalDate> lastSalesDateMap = validOrderDetails.stream()
                .filter(od -> od.getTOrder().getOrderDateTime() != null)
                .collect(Collectors.groupingBy(
                        od -> od.getGoodsNo() + "_" + od.getTOrder().getDestinationNo(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(od -> od.getTOrder().getOrderDateTime())),
                                opt -> opt.map(od -> od.getTOrder().getOrderDateTime().toLocalDate()).orElse(null)
                        )));

        // 両方のMapのキーを統合して更新
        Set<String> allKeys = new HashSet<>(orderNumMap.keySet());
        allKeys.addAll(lastSalesDateMap.keySet());

        allKeys.forEach(key -> {
            String[] ids = key.split("_");
            int goodsNo = Integer.parseInt(ids[0]);
            int destinationNo = Integer.parseInt(ids[1]);
            BigDecimal orderNum = orderNumMap.getOrDefault(key, BigDecimal.ZERO);
            LocalDate lastSalesDate = lastSalesDateMap.get(key);
            this.updatePartnerGoods(partnerNo, goodsNo, destinationNo, orderNum, lastSalesDate);
        });
    }

    private void updatePartnerGoods(Integer partnerNo, Integer goodsNo, Integer destinationNo, BigDecimal orderNum, LocalDate lastSalesDate) {
        MPartnerGoods mPartnerGoods = this.partnerGoodsService.getByPK(MPartnerGoodsPK.builder().partnerNo(partnerNo).goodsNo(goodsNo).destinationNo(destinationNo).build());
        if (mPartnerGoods == null) {
            log.warn(String.format("得意先商品が見つかりません。partnerNo:%d, goodsNo:%d, destinationNo:%d", partnerNo, goodsNo, destinationNo));
            return;
        }
        try {
            mPartnerGoods.setOrderNumPerYear(orderNum);
            // 既存の最終売上日より新しい場合のみ更新
            if (lastSalesDate != null && (mPartnerGoods.getLastSalesDate() == null || lastSalesDate.isAfter(mPartnerGoods.getLastSalesDate()))) {
                mPartnerGoods.setLastSalesDate(lastSalesDate);
            }
            log.info(String.format("得意先商品更新 partnerNo:%d, destinationNo:%d, goods_no:%d, orderNum:%s, lastSalesDate:%s",
                    partnerNo, destinationNo, goodsNo, orderNum.toString(), mPartnerGoods.getLastSalesDate()));
            this.partnerGoodsService.update(mPartnerGoods);
        } catch (Exception e) {
            log.error(String.format("得意先商品の更新に失敗しました。partnerNo:%d, goodsNo:%d, destinationNo:%d, 原因:%s",
                    partnerNo, goodsNo, destinationNo, e.getMessage()));
        }
    }
}
