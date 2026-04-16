package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartChangeHistory;
import jp.co.oda32.domain.model.bcart.BCartProducts;
import jp.co.oda32.domain.service.bcart.BCartChangeHistoryService;
import jp.co.oda32.domain.service.bcart.BCartProductsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * B-CART商品説明反映バッチ
 * b_cart_change_history で PRODUCT の未反映レコードの対象商品を
 * PATCH /api/v1/products/{id} でB-CARTに反映する
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartProductDescriptionUpdateTasklet implements Tasklet {

    private final BCartProductsService productsService;
    private final BCartChangeHistoryService changeHistoryService;
    @Qualifier("bCartHttpClient")
    private final OkHttpClient httpClient;
    private static final int RATE_LIMIT_BATCH = 250;
    private static final long RATE_LIMIT_WAIT_MS = 5 * 60 * 1000;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 未反映の変更履歴を取得し、対象商品IDを特定
        List<BCartChangeHistory> unreflectedHistories = changeHistoryService.findUnreflectedByType("PRODUCT");
        Set<Long> targetProductIds = unreflectedHistories.stream()
                .map(BCartChangeHistory::getTargetId)
                .collect(Collectors.toSet());

        log.info("Found {} unreflected product descriptions ({} unique products)",
                unreflectedHistories.size(), targetProductIds.size());

        // 履歴をproductIdでグルーピング
        Map<Long, List<BCartChangeHistory>> historyMap = unreflectedHistories.stream()
                .collect(Collectors.groupingBy(BCartChangeHistory::getTargetId));

        // 対象商品を一括取得（N+1 クエリ回避）
        List<Integer> targetIds = targetProductIds.stream().map(Long::intValue).collect(Collectors.toList());
        Map<Long, BCartProducts> productMap = productsService.findAllById(targetIds).stream()
                .collect(Collectors.toMap(p -> p.getId().longValue(), p -> p, (a, b) -> a));

        int successCount = 0;
        int requestCount = 0;
        for (Long productId : targetProductIds) {
            // レート制限
            if (requestCount > 0 && requestCount % RATE_LIMIT_BATCH == 0) {
                log.info("Rate limit reached ({} requests). Waiting 5 minutes...", requestCount);
                Thread.sleep(RATE_LIMIT_WAIT_MS);
            }
            requestCount++;

            BCartProducts product = productMap.get(productId);
            if (product == null) continue;
            try {
                boolean success = patchProduct(product);
                if (success) {
                    List<BCartChangeHistory> histories = historyMap.getOrDefault(productId, List.of());
                    for (BCartChangeHistory h : histories) {
                        changeHistoryService.markReflected(h.getId());
                    }
                    successCount++;
                    log.info("Updated product {} ({})", product.getId(), product.getName());
                }
            } catch (IOException e) {
                log.error("Failed to update product {}: {}", product.getId(), e.getMessage());
            }
        }

        log.info("B-CART product description update completed: {}/{} succeeded",
                successCount, targetProductIds.size());
        return RepeatStatus.FINISHED;
    }

    private boolean patchProduct(BCartProducts product) throws IOException {
        OkHttpClient client = this.httpClient;

        FormBody.Builder formBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
        addIfNotNull(formBuilder, "name", product.getName());
        addIfNotNull(formBuilder, "catch_copy", product.getCatchCopy());
        addIfNotNull(formBuilder, "description", product.getDescription());
        addIfNotNull(formBuilder, "prepend_text", product.getPrependText());
        addIfNotNull(formBuilder, "append_text", product.getAppendText());
        addIfNotNull(formBuilder, "middle_text", product.getMiddleText());
        addIfNotNull(formBuilder, "rv_prepend_text", product.getRvPrependText());
        addIfNotNull(formBuilder, "rv_append_text", product.getRvAppendText());
        addIfNotNull(formBuilder, "rv_middle_text", product.getRvMiddleText());
        addIfNotNull(formBuilder, "meta_title", product.getMetaTitle());
        addIfNotNull(formBuilder, "meta_keywords", product.getMetaKeywords());
        addIfNotNull(formBuilder, "meta_description", product.getMetaDescription());

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.bcart.jp")
                .addPathSegment("api")
                .addPathSegment("v1")
                .addPathSegment("products")
                .addPathSegment(String.valueOf(product.getId()))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .patch(formBuilder.build())
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("PATCH products/{} failed: {} - {}", product.getId(), response.code(),
                        response.body() != null ? response.body().string() : "");
                return false;
            }
            return true;
        }
    }

    private void addIfNotNull(FormBody.Builder builder, String key, String value) {
        if (value != null) {
            builder.add(key, value);
        }
    }
}
