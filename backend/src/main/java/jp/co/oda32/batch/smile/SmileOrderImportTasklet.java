package jp.co.oda32.batch.smile;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Smile売上明細ワークテーブルを使用して
 * 本システムに注文データを登録、更新するタスクレットクラス
 *
 * @author k_oda
 * @since 2024/05/08
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class SmileOrderImportTasklet implements Tasklet {
    @NonNull
    NewSmileOrderProcessor newSmileOrderProcessor;
    @NonNull
    UpdateSmileOrderProcessor updateSmileOrderProcessor;
    @NonNull
    DeleteSmileOrderProcessor deleteSmileOrderProcessor;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        log.info("新規受注登録処理開始");
        newSmileOrderProcessor.newOrderProcess();
        log.info("受注更新処理開始");
        updateSmileOrderProcessor.modifiedOrderProcess();
        log.info("受注削除処理開始");
        deleteSmileOrderProcessor.deleteOrderProcess();
        log.info("SMILE受注処理終了");
        return RepeatStatus.FINISHED;
    }
}
