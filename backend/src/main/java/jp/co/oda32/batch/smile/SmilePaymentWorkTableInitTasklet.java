package jp.co.oda32.batch.smile;

import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * SMILEペイメント処理用のワークテーブル初期化タスクレット
 * バッチジョブ実行前にワークテーブルを初期化するための専用タスクレット
 */
@Component
@Log4j2
@StepScope
public class SmilePaymentWorkTableInitTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    public SmilePaymentWorkTableInitTasklet(JdbcTemplate jdbcTemplate,
                                            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        TransactionStatus status = null;
        try {
            // トランザクションを手動で開始
            status = transactionManager.getTransaction(new DefaultTransactionDefinition());

            // ワークテーブルの初期化処理 - JDBCテンプレートで直接実行
            log.info("SmilePaymentワークテーブルを初期化します...");
            jdbcTemplate.execute("TRUNCATE TABLE w_smile_payment");

            // トランザクションをコミット
            transactionManager.commit(status);

            log.info("SmilePaymentワークテーブルを初期化しました");
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            // エラー発生時はロールバック
            if (status != null && !status.isCompleted()) {
                transactionManager.rollback(status);
            }
            log.error("SmilePaymentワークテーブルの初期化に失敗しました", e);
            throw e;
        }
    }
}