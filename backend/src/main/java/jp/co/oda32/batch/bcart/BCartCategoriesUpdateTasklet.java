package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartCategories;
import jp.co.oda32.domain.model.bcart.BCartChangeHistory;
import jp.co.oda32.domain.service.bcart.BCartCategoriesService;
import jp.co.oda32.domain.service.bcart.BCartChangeHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * B-CARTカテゴリ反映バッチ
 * b_cart_reflected=false のカテゴリを PATCH /api/v1/categories/{id} でB-CARTに反映
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartCategoriesUpdateTasklet implements Tasklet {
    private final BCartCategoriesService bCartCategoriesService;
    private final BCartChangeHistoryService changeHistoryService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<BCartCategories> unreflected = bCartCategoriesService.findUnreflected();
        log.info("Found {} unreflected categories to update", unreflected.size());

        // 履歴を一括取得してIDでグルーピング（N+1回避）
        Map<Long, List<BCartChangeHistory>> historyMap = changeHistoryService
                .findUnreflectedByType("CATEGORY").stream()
                .collect(java.util.stream.Collectors.groupingBy(BCartChangeHistory::getTargetId));

        int successCount = 0;
        for (BCartCategories category : unreflected) {
            try {
                boolean success = patchCategory(category);
                if (success) {
                    category.setBCartReflected(true);
                    bCartCategoriesService.save(category);

                    // 変更履歴のreflectedフラグも更新
                    List<BCartChangeHistory> histories = historyMap
                            .getOrDefault(Long.valueOf(category.getId()), List.of());
                    for (BCartChangeHistory h : histories) {
                        changeHistoryService.markReflected(h.getId());
                    }

                    successCount++;
                    log.info("Updated category {} ({})", category.getId(), category.getName());
                }
            } catch (Exception e) {
                log.error("Failed to update category {}: {}", category.getId(), e.getMessage());
            }
        }

        log.info("B-CART categories update completed: {}/{} succeeded", successCount, unreflected.size());
        return RepeatStatus.FINISHED;
    }

    private boolean patchCategory(BCartCategories category) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().build();

        // form-urlencoded形式でリクエストボディを構築
        FormBody.Builder formBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
        formBuilder.add("name", nullToEmpty(category.getName()));
        formBuilder.add("description", nullToEmpty(category.getDescription()));
        formBuilder.add("rv_description", nullToEmpty(category.getRvDescription()));
        formBuilder.add("meta_title", nullToEmpty(category.getMetaTitle()));
        formBuilder.add("meta_keywords", nullToEmpty(category.getMetaKeywords()));
        formBuilder.add("meta_description", nullToEmpty(category.getMetaDescription()));
        formBuilder.add("priority", String.valueOf(category.getPriority()));
        formBuilder.add("flag", String.valueOf(category.getFlag()));
        if (category.getParentCategoryId() != null) {
            formBuilder.add("parent_category_id", String.valueOf(category.getParentCategoryId()));
        }

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.bcart.jp")
                .addPathSegment("api")
                .addPathSegment("v1")
                .addPathSegment("categories")
                .addPathSegment(String.valueOf(category.getId()))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .patch(formBuilder.build())
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("PATCH categories/{} failed: {} - {}", category.getId(), response.code(),
                        response.body() != null ? response.body().string() : "");
                return false;
            }
            return true;
        }
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
