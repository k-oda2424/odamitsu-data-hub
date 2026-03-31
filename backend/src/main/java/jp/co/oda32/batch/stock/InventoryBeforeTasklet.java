package jp.co.oda32.batch.stock;

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
 * 棚卸し用に在庫履歴をdeleteするクラス
 *
 * @author k_oda
 * @since 2019/07/16
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class InventoryBeforeTasklet extends AbstractInvoiceDateManagement implements Tasklet {
    @NonNull
    private StockManager stockManager;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("棚卸ファイル処理前に今回登録する棚卸用在庫履歴を削除します。");
        this.stockManager.deleteTStockLogForInventory(getInventoryDateTime());
        return RepeatStatus.FINISHED;
    }
}
