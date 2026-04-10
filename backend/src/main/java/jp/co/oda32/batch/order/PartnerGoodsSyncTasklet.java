package jp.co.oda32.batch.order;

import jp.co.oda32.domain.repository.goods.MPartnerGoodsRepository;
import jp.co.oda32.domain.service.goods.MPartnerGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 得意先商品マスタ同期バッチ
 * 注文明細から得意先商品マスタの登録・更新を一括で行う。
 * - 年間注文数量を0にリセット
 * - 過去1年の注文データから一括UPSERT（INSERT ... ON CONFLICT）
 *
 * @author k_oda
 * @since 2019/04/11
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class PartnerGoodsSyncTasklet implements Tasklet {

    private final MPartnerGoodsService partnerGoodsService;
    private final MPartnerGoodsRepository partnerGoodsRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // Step 1: 全ての年間注文数量を0にする
        int cleared = this.partnerGoodsService.updateAllClearOrderNumPerYear();
        log.info("全年間注文数量を0に更新: {}件", cleared);

        // Step 2: 過去1年の注文データから一括UPSERT
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        int upserted = this.partnerGoodsRepository.bulkUpsertFromOrderDetails(oneYearAgo);
        log.info("得意先商品一括同期完了: {}件", upserted);

        return RepeatStatus.FINISHED;
    }
}
