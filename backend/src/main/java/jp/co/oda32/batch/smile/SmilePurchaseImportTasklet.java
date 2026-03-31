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
 * Smile仕入明細ワークテーブルを使用して
 * 本システムに仕入データを登録、更新するタスクレットクラス
 *
 * @author k_oda
 * @since 2024/09/11
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class SmilePurchaseImportTasklet implements Tasklet {
    @NonNull
    NewSmilePurchaseProcessor newSmilePurchaseProcessor;
    @NonNull
    UpdateSmilePurchaseProcessor updateSmilePurchaseProcessor;
    @NonNull
    DeleteSmilePurchaseProcessor deleteSmilePurchaseProcessor;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        log.info("新規仕入登録処理開始");
        newSmilePurchaseProcessor.newPurchaseProcess();
        log.info("仕入更新処理開始");
        updateSmilePurchaseProcessor.modifiedPurchaseProcess();
        log.info("仕入削除処理開始");
        deleteSmilePurchaseProcessor.deletePurchaseProcess();
        log.info("SMILE仕入処理終了");
        return RepeatStatus.FINISHED;
    }
}
