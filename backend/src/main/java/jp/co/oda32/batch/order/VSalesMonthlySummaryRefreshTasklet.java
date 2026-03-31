package jp.co.oda32.batch.order;

import jp.co.oda32.domain.service.order.VSalesMonthlySummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * 月間売上マテリアライズドビューをリフレッシュするクラス
 *
 * @author k_oda
 * @since 2020/03/13
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class VSalesMonthlySummaryRefreshTasklet implements Tasklet {
    @NonNull
    private VSalesMonthlySummaryService vSalesMonthlySummaryService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        this.vSalesMonthlySummaryService.refresh();
        return RepeatStatus.FINISHED;
    }
}
