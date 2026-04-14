package jp.co.oda32.batch.bcart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartLogistics;
import jp.co.oda32.domain.model.bcart.BCartOrder;
import jp.co.oda32.domain.model.bcart.BCartOrderProduct;
import jp.co.oda32.domain.model.bcart.BCartOrdersApiResponse;
import jp.co.oda32.domain.service.bcart.BCartLogisticsService;
import jp.co.oda32.domain.service.bcart.BCartOrderProductService;
import jp.co.oda32.domain.service.bcart.BCartOrderService;
import jp.co.oda32.util.gson.CustomNumberDeserializer;
import jp.co.oda32.util.gson.LocalDateTypeAdapter;
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
import java.time.LocalDate;
import java.util.List;

/**
 * B-Cart新規受注データをb_cart_orderに登録するタスクレットクラス
 * B-Cart→本システムへの受注登録
 *
 * @author k_oda
 * @since 2023/03/16
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartOrderRegisterTasklet implements Tasklet {
    private final BCartOrderService bCartOrderService;
    private final BCartOrderProductService bCartOrderProductService;
    private final BCartLogisticsService bCartLogisticsService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        try (Response response = executeBCartOrdersAPI()) {
            if (!response.isSuccessful()) {
                log.error("API call failed: " + response.code());
                return RepeatStatus.CONTINUABLE;
            }

            if (response.body() == null) {
                log.error("Response body is null");
                return RepeatStatus.CONTINUABLE;
            }

            log.info(response);
            String jsonResponse = response.body().string();
            log.debug("JSON response: {}", jsonResponse);

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Integer.class, new CustomNumberDeserializer<>(Integer.class))
                    .registerTypeAdapter(Double.class, new CustomNumberDeserializer<>(Double.class))
                    .registerTypeAdapter(Float.class, new CustomNumberDeserializer<>(Float.class))
                    .registerTypeAdapter(Long.class, new CustomNumberDeserializer<>(Long.class))
                    .registerTypeAdapter(LocalDate.class, new LocalDateTypeAdapter())
                    .create();

            BCartOrdersApiResponse bCartOrdersApiResponse = gson.fromJson(jsonResponse, BCartOrdersApiResponse.class);
            List<BCartOrder> bCartOrderList = bCartOrdersApiResponse.getBCartOrderList();

            for (BCartOrder bCartOrder : bCartOrderList) {
                saveBCartOrder(bCartOrder);
            }
        } catch (IOException e) {
            log.error("Failed to execute B-Cart API: ", e);
            return RepeatStatus.CONTINUABLE;
        }

        return RepeatStatus.FINISHED;
    }

    private Response executeBCartOrdersAPI() throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.bcart.jp")
                .addPathSegment("api")
                .addPathSegment("v1")
                .addPathSegment("orders")
                .addQueryParameter("status", "新規注文")
                .addQueryParameter("complete", "1")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
                .build();

        return client.newCall(request).execute();
    }

    private void saveBCartOrder(BCartOrder bCartOrder) {
        bCartOrderService.save(bCartOrder);
        List<BCartOrderProduct> bCartOrderProductList = bCartOrder.getOrderProductList();
        bCartOrderProductService.save(bCartOrderProductList);
        List<BCartLogistics> bCartLogisticsList = bCartOrder.getBCartLogisticsList();
        bCartLogisticsService.save(bCartLogisticsList);
    }
}
