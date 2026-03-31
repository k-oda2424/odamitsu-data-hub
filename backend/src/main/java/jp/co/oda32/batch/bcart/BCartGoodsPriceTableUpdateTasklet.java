package jp.co.oda32.batch.bcart;

import jp.co.oda32.domain.service.bcart.BCartProductSetsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * B-Cart商品価格関連テーブルの更新タスクレット
 * 本システムの価格をB-Cart商品価格関連のテーブルに反映する
 * b_cart_product_sets
 * b_cart_special_price
 * b_cart_volume_discount
 * b_cart_group_price
 *
 * @author k_oda
 * @since 2023/07/07
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartGoodsPriceTableUpdateTasklet implements Tasklet {
    private final BCartProductSetsService bCartProductSetsService;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        // 更新対象を取得 価格が違うもの v_b_cart_products_price_change

        // b-cart用テーブルに更新をかける

        return RepeatStatus.FINISHED;
    }

}
