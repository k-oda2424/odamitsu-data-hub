package jp.co.oda32.batch.bcart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartProducts;
import jp.co.oda32.domain.model.bcart.BCartProductsApiResponse;
import jp.co.oda32.domain.service.bcart.BCartProductsService;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.gson.CustomDateTypeAdapter;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @author k_oda
 * @since 2023/04/21
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartProductsImportTasklet implements Tasklet {
    private final BCartProductsService bCartProductsService;
    @Qualifier("bCartHttpClient")
    private final OkHttpClient httpClient;
    private final int API_LIMIT = 100;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        int i = 0;
        while (true) {
            try (Response response = executeBCartProductsAPI(i)) {
                if (!response.isSuccessful()) {
                    // RepeatStatus.CONTINUABLE は「もう一度 execute を呼べ」の意味で無限ループリスクがある。
                    // 失敗時は例外を throw してバッチを失敗扱いにする。
                    throw new RuntimeException("B-Cart 商品 API 呼び出しが失敗しました: code=" + response.code());
                }

                if (response.body() == null) {
                    throw new RuntimeException("B-Cart 商品 API のレスポンスボディが null です");
                }

                log.info(response);
                String jsonResponse = response.body().string();
                log.debug("JSON response: {}", jsonResponse);

                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(Integer.class, new CustomNumberDeserializer<>(Integer.class))
                        .registerTypeAdapter(Double.class, new CustomNumberDeserializer<>(Double.class))
                        .registerTypeAdapter(Float.class, new CustomNumberDeserializer<>(Float.class))
                        .registerTypeAdapter(Long.class, new CustomNumberDeserializer<>(Long.class))
                        .registerTypeAdapter(Date.class, new CustomDateTypeAdapter("yyyy-MM-dd HH:mm:ss"))
                        .create();

                BCartProductsApiResponse bCartProductsResponse = gson.fromJson(jsonResponse, BCartProductsApiResponse.class);
                List<BCartProducts> bCartProductsList = bCartProductsResponse.getBCartProductsList();

                if (CollectionUtil.isEmpty(bCartProductsList)) {
                    // 空になったら終わり
                    break;
                }
                for (BCartProducts bCartProducts : bCartProductsList) {
                    bCartProductsService.save(bCartProducts);
                }
            }
            // IOException は try-with-resources から自動的に伝播される（catch せずに batch を失敗扱いにする）
            i++;
        }

        return RepeatStatus.FINISHED;
    }

    private Response executeBCartProductsAPI(int i) throws IOException {
        OkHttpClient client = this.httpClient;
        int offset = API_LIMIT * i;
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.bcart.jp")
                .addPathSegment("api")
                .addPathSegment("v1")
                .addPathSegment("products")
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
