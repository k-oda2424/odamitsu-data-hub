package jp.co.oda32.batch.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.service.goods.MPartnerGoodsService;
import jp.co.oda32.domain.service.master.MPartnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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
    private final MPartnerService partnerService;
    @NonNull
    private final MPartnerGoodsService partnerGoodsService;
    @NonNull
    private final PartnerGoodsSyncProcessor processor;

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
            int[] result = processor.partnerOrderProcess(partner.getShopNo(), partner.getCompanyNo(), partner.getPartnerNo());
            insertCount += result[0];
            updateCount += result[1];
        }
        log.info("得意先商品同期完了。新規登録:{}件, 更新:{}件", insertCount, updateCount);

        return RepeatStatus.FINISHED;
    }
}
