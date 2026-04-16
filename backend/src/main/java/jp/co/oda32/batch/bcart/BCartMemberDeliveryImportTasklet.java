package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartMemberOtherAddresses;
import jp.co.oda32.domain.service.bcart.BCartMemberOtherAddressesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartMemberDeliveryImportTasklet implements Tasklet {
    @Qualifier("bCartHttpClient")
    private final OkHttpClient httpClient;
    private final BCartMemberOtherAddressesService bCartMemberOtherAddressesService;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        String url = "https://api.bcart.jp/api/v1/other_addresses";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("response:" + response);
                throw new IOException("Unexpected code " + response);
            }

            JSONObject responseJson = new JSONObject(response.body().string());
            JSONArray deliveryInfoArray = responseJson.getJSONArray("other_addresses");
            registerMemberDeliveryInfo(deliveryInfoArray);
        }

        return RepeatStatus.FINISHED;
    }

    private void registerMemberDeliveryInfo(JSONArray deliveryInfoArray) {
        for (int i = 0; i < deliveryInfoArray.length(); i++) {
            JSONObject deliveryInfo = deliveryInfoArray.getJSONObject(i);
            BCartMemberOtherAddresses memberOtherAddresses = BCartMemberOtherAddresses.builder()
                    .id(deliveryInfo.getLong("id"))
                    .customerId(deliveryInfo.getLong("customer_id"))
                    .destinationCode(deliveryInfo.isNull("destination_code") ? null : deliveryInfo.getString("destination_code"))
                    .compName(deliveryInfo.isNull("comp_name") ? null : deliveryInfo.getString("comp_name"))
                    .department(deliveryInfo.isNull("department") ? null : deliveryInfo.getString("department"))
                    .name(deliveryInfo.getString("name"))
                    .zip(deliveryInfo.getString("zip"))
                    .pref(deliveryInfo.getString("pref"))
                    .address1(deliveryInfo.getString("address1"))
                    .address2(deliveryInfo.getString("address2"))
                    .address3(deliveryInfo.isNull("address3") ? null : deliveryInfo.getString("address3"))
                    .tel(deliveryInfo.getString("tel"))
                    .build();
            bCartMemberOtherAddressesService.save(memberOtherAddresses);
        }
    }

}
