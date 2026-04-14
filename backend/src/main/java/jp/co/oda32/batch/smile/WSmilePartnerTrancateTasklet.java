package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.service.master.WSmilePartnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Smile得意先ワークテーブルを全削除するタスクレットクラス
 *
 * @author k_oda
 * @since 2024/06/12
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class WSmilePartnerTrancateTasklet implements Tasklet {
    private final WSmilePartnerService wSmilePartnerService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        this.wSmilePartnerService.truncateTable();
        return RepeatStatus.FINISHED;
    }
}
