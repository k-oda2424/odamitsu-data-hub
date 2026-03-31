package jp.co.oda32.batch.smile;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * Smile仕入明細ワークテーブルを全削除するタスクレットクラス
 *
 * @author k_oda
 * @since 2024/09/11
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class WSmilePurchaseOutputFileTrancateTasklet implements Tasklet {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("WSmilePurchaseOutputFileテーブルのクリア処理を開始します");
        
        try {
            // TRUNCATE前の行数を確認
            Number beforeCount = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM w_smile_purchase_output_file").getSingleResult();
            log.info("クリア前の行数: {}", beforeCount);
            
            if (beforeCount.intValue() == 0) {
                log.info("テーブルは既に空です。処理をスキップします。");
                return RepeatStatus.FINISHED;
            }
            
            try {
                // まずTRUNCATEを試行
                log.info("TRUNCATE処理を実行します");
                entityManager.createNativeQuery("TRUNCATE TABLE w_smile_purchase_output_file RESTART IDENTITY").executeUpdate();
                entityManager.flush();
                log.info("TRUNCATE処理が完了しました");
            } catch (Exception truncateException) {
                log.warn("TRUNCATE処理が失敗しました。DELETE処理に切り替えます。エラー: {}", truncateException.getMessage());
                // TRUNCATEが失敗した場合はDELETEを使用
                int deletedRows = entityManager.createNativeQuery("DELETE FROM w_smile_purchase_output_file").executeUpdate();
                entityManager.flush();
                log.info("DELETE処理が完了しました。削除行数: {}", deletedRows);
            }
            
            // クリア後の行数を確認
            Number afterCount = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM w_smile_purchase_output_file").getSingleResult();
            log.info("クリア後の行数: {}", afterCount);
            
            if (afterCount.intValue() == 0) {
                log.info("テーブルクリア処理が正常に完了しました");
            } else {
                log.error("テーブルクリア処理後もレコードが残っています: {} 行", afterCount);
                throw new RuntimeException("テーブルクリア処理が完全に実行されませんでした");
            }
            
        } catch (Exception e) {
            log.error("テーブルクリア処理でエラーが発生しました", e);
            throw e;
        }
        
        log.info("WSmilePurchaseOutputFileテーブルのクリア処理が完了しました");
        return RepeatStatus.FINISHED;
    }
}
