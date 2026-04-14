package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.service.smile.WSmileOrderOutputFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Smile売上明細ワークテーブルを全削除するタスクレットクラス
 *
 * @author k_oda
 * @since 2024/05/08
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class WSmileOrderOutputFileTrancateTasklet implements Tasklet {
    private final WSmileOrderOutputFileService wSmileOrderOutputFileService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        this.wSmileOrderOutputFileService.truncateTable();
        return RepeatStatus.FINISHED;
    }
}
