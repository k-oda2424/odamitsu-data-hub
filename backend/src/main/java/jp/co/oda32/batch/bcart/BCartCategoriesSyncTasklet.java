package jp.co.oda32.batch.bcart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartCategories;
import jp.co.oda32.domain.model.bcart.BCartCategoriesApiResponse;
import jp.co.oda32.domain.service.bcart.BCartCategoriesService;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.gson.CustomNumberDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * B-CARTカテゴリ同期バッチ
 * GET /api/v1/categories → b_cart_categories テーブルにUPSERT
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartCategoriesSyncTasklet implements Tasklet {
    private final BCartCategoriesService bCartCategoriesService;
    private final int API_LIMIT = 100;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // B-CARTに存在するカテゴリIDを収集（削除検知用）
        Set<Integer> apiCategoryIds = new HashSet<>();

        int i = 0;
        while (true) {
            try (Response response = executeBCartCategoriesAPI(i)) {
                if (!response.isSuccessful()) {
                    log.error("B-CART Categories API call failed: " + response.code());
                    throw new RuntimeException("B-CART Categories API call failed");
                }

                if (response.body() == null) {
                    log.error("Response body is null");
                    throw new RuntimeException("B-CART Categories API call failed");
                }

                String jsonResponse = response.body().string();
                log.debug("JSON response: {}", jsonResponse);

                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(Integer.class, new CustomNumberDeserializer<>(Integer.class))
                        .create();

                BCartCategoriesApiResponse apiResponse = gson.fromJson(jsonResponse, BCartCategoriesApiResponse.class);
                List<BCartCategories> categoriesList = apiResponse.getBCartCategoriesList();

                if (CollectionUtil.isEmpty(categoriesList)) {
                    break;
                }

                for (BCartCategories category : categoriesList) {
                    apiCategoryIds.add(category.getId());

                    bCartCategoriesService.findById(category.getId()).ifPresentOrElse(
                            existing -> {
                                if (existing.isBCartReflected()) {
                                    category.setBCartReflected(true);
                                    category.setVersion(existing.getVersion());
                                    category.setCreatedAt(existing.getCreatedAt());
                                    bCartCategoriesService.save(category);
                                }
                            },
                            () -> {
                                category.setBCartReflected(true);
                                category.setVersion(0);
                                bCartCategoriesService.save(category);
                            }
                    );
                }

                log.info("Synced {} categories (page {})", categoriesList.size(), i);
            } catch (IOException e) {
                log.error("Failed to execute BCartCategoriesApi: ", e);
                throw new RuntimeException("B-CART Categories API call failed");
            }
            i++;
        }

        // B-CARTに存在しないカテゴリをflag=0（非表示）に更新
        List<BCartCategories> allLocal = bCartCategoriesService.findAll();
        int removedCount = 0;
        for (BCartCategories local : allLocal) {
            if (!apiCategoryIds.contains(local.getId()) && local.getFlag() != 0) {
                local.setFlag(0);
                local.setBCartReflected(true); // B-CART側で削除済みなので反映不要
                bCartCategoriesService.save(local);
                removedCount++;
                log.info("Category {} ({}) marked as hidden (deleted from B-CART)", local.getId(), local.getName());
            }
        }

        log.info("B-CART categories sync completed. Synced: {}, Removed: {}", apiCategoryIds.size(), removedCount);
        return RepeatStatus.FINISHED;
    }

    private Response executeBCartCategoriesAPI(int i) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        int offset = API_LIMIT * i;
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.bcart.jp")
                .addPathSegment("api")
                .addPathSegment("v1")
                .addPathSegment("categories")
                .addQueryParameter("limit", String.valueOf(API_LIMIT))
                .addQueryParameter("offset", String.valueOf(offset))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
                .build();

        return client.newCall(request).execute();
    }
}
