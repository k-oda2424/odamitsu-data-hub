package jp.co.oda32.batch.estimate;

import jp.co.oda32.constant.EstimateStatus;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.service.estimate.TEstimateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 親得意先見積作成完了した子見積を同グループ見積済みにするタスクレットクラス
 *
 * @author k_oda
 * @since 2023/01/23
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class ParentEstimateCreatedTasklet implements Tasklet {
    @NonNull
    TEstimateService tEstimateService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 親見積が作成された子見積 見積ステータスが「作成」か「修正」のもの
        List<TEstimate> updateChildrenList = this.tEstimateService.findChildrenEstimate();
        // 見積ステータスを「同グループ提出済み」に更新する
        for (TEstimate children : updateChildrenList) {
            children.setEstimateStatus(EstimateStatus.OTHER_PARTNER_NOTIFIED.getCode());
            this.tEstimateService.update(children);
        }
        return RepeatStatus.FINISHED;
    }
}
