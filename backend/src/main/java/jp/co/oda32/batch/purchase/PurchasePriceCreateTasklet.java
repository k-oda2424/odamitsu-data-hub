package jp.co.oda32.batch.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.service.purchase.MPurchasePriceService;
import jp.co.oda32.domain.service.purchase.TPurchaseDetailService;
import jp.co.oda32.util.BigDecimalUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 仕入価格マスタを作成するタスクレットクラス
 *
 * @author k_oda
 * @since 2020/01/22
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class PurchasePriceCreateTasklet implements Tasklet {
    @NonNull
    private TPurchaseDetailService tPurchaseDetailService;
    @NonNull
    private MPurchasePriceService mPurchasePriceService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("仕入価格マスタ作成処理開始");
        // 仕入明細を取得
        List<TPurchaseDetail> latestPurchaseDetailList = this.tPurchaseDetailService.findLatestPurchasePrice();
        if (latestPurchaseDetailList.isEmpty()) {
            log.info("仕入価格マスタ作成する仕入がありません。処理終了。");
            return RepeatStatus.FINISHED;
        }
        latestPurchaseDetailList.forEach(tPurchaseDetail -> log.info(String.format("仕入日：%s,商品番号：%d,商品名:%s,価格：%s円", tPurchaseDetail.getPurchaseDate(), tPurchaseDetail.getGoodsNo(), tPurchaseDetail.getGoodsName(), tPurchaseDetail.getGoodsPrice())));
        List<MPurchasePrice> mPurchasePriceList = latestPurchaseDetailList.stream()
                // 仕入価格0円より大きいもの（値引きデータは仕入価格に含めない）
                .filter(tPurchaseDetail -> tPurchaseDetail.getGoodsPrice().compareTo(BigDecimal.ZERO) > 0)
                // 数量が入力されていないものは仕入価格に含めない
                .filter(tPurchaseDetail -> BigDecimalUtil.isPositive(tPurchaseDetail.getGoodsNum()))
                .map(this::convertTPurchaseDetailToMPurchasePrice)
                .distinct()
                .collect(Collectors.toList());

        this.mPurchasePriceService.insert(mPurchasePriceList);
        this.tPurchaseDetailService.updateAllPurchasePriceReflect();
        log.info("仕入価格マスタ作成処理終了");
        return RepeatStatus.FINISHED;
    }

    private MPurchasePrice convertTPurchaseDetailToMPurchasePrice(TPurchaseDetail tPurchaseDetail) {
        return MPurchasePrice.builder()
                .supplierNo(tPurchaseDetail.getTPurchase().getSupplierNo())
                .goodsNo(tPurchaseDetail.getGoodsNo())
                .shopNo(tPurchaseDetail.getShopNo())
                .partnerNo(0)
                .destinationNo(0)
                .goodsPrice(tPurchaseDetail.getGoodsPrice())
                .taxCategory(tPurchaseDetail.getTaxCategory())
                .includeTaxFlg(Flag.NO.getValue())
                .taxRate(tPurchaseDetail.getTaxRate())
                .note(tPurchaseDetail.getTPurchase().getNote())
                .lastPurchaseDate(tPurchaseDetail.getPurchaseDate())
                .build();
    }
}
