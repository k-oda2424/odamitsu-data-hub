package jp.co.oda32.batch.bcart;

import com.google.gson.*;
import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartMember;
import jp.co.oda32.domain.model.bcart.BCartMemberApiResponse;
import jp.co.oda32.domain.service.bcart.BCartMemberService;
import jp.co.oda32.util.CollectionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * B-Cart会員マッピング同期タスクレット
 *
 * @author k_oda
 * @since 2023/05/29
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartMemberImportTasklet implements Tasklet {
    private final BCartMemberService bCartMemberService;
    private final JobExplorer jobExplorer;
    @Qualifier("bCartHttpClient")
    private final OkHttpClient httpClient;
    private final int API_LIMIT = 100;
    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        // 会員登録
        processMembers("created_at_min");
        // 会員更新
        processMembers("updated_at_min");
        return RepeatStatus.FINISHED;
    }

    public void processMembers(String timeParameter) throws IOException {
        String accessToken = BCartApiConfig.getInstance().getAccessToken();
        LocalDateTime lastBatchRunTime = getLastBatchRunTime();
        OkHttpClient client = this.httpClient;

        int offset = 0;
        boolean moreData = true;

        while (moreData) {
            HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
                    .scheme("https")
                    .host("api.bcart.jp")
                    .addPathSegment("api")
                    .addPathSegment("v1")
                    .addPathSegment("customers")
                    .addQueryParameter("limit", String.valueOf(API_LIMIT))
                    .addQueryParameter("offset", String.valueOf(offset));
            // 'status' パラメータは指定しない

            if (lastBatchRunTime != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedDate = lastBatchRunTime.format(formatter);
                urlBuilder.addQueryParameter(timeParameter, formattedDate);
            }

            HttpUrl url = urlBuilder.build();
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            String responseBody;
            try (Response response = client.newCall(request).execute()) {
                responseBody = response.body() != null ? response.body().string() : "";
            }

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDate.class, new JsonDeserializer<LocalDate>() {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                        @Override
                        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                            return LocalDate.parse(json.getAsString(), formatter);
                        }
                    })
                    .create();
            BCartMemberApiResponse bCartMemberApiResponse = gson.fromJson(responseBody, BCartMemberApiResponse.class);

            if (bCartMemberApiResponse == null || bCartMemberApiResponse.getBCartMemberList() == null) {
                // レスポンスが期待通りでない場合はエラーログを出力
                log.error("API response is null or does not contain member list.");
                moreData = false;
                break;
            }

            List<BCartMember> bCartMemberList = bCartMemberApiResponse.getBCartMemberList();

            if (CollectionUtil.isEmpty(bCartMemberList)) {
                // データがない場合はループを終了
                moreData = false;
                break;
            }

            // 一度のストリームでフィルタリングと処理を実行
            List<BCartMember> processedMembers = bCartMemberList.stream()
                    // '未承認' または '無効' の会員を除外
                    .filter(member -> !"未承認".equals(member.getStatus()) && !"無効".equals(member.getStatus()))
                    // SmilePartnerMasterLinked を false に設定し、extId をチェック
                    .peek(member -> {
                        member.setSmilePartnerMasterLinked(false);
                        if (member.getExtId() == null || member.getExtId().isEmpty()) {
                            log.warn("APIから取得した会員ID: {} にextIdが設定されていません（既存データがあれば保持されます）", member.getId());
                        }
                    })
                    .collect(Collectors.toList());

            if (CollectionUtil.isEmpty(processedMembers)) {
                // フィルタリング後にデータがない場合、次のページを取得
                offset += API_LIMIT;
                continue;
            }

            // 会員情報を更新
            this.bCartMemberService.updateMembers(processedMembers);

            // 取得した件数が API_LIMIT 未満の場合、データはこれ以上ない
            if (bCartMemberList.size() < API_LIMIT) {
                moreData = false;
            }

            // 次のページを取得するためにオフセットを更新
            offset += API_LIMIT;
        }
    }



    private LocalDateTime getLastBatchRunTime() {
        List<JobInstance> jobInstances = jobExplorer.getJobInstances("BCartMemberUpdateBatch", 0, 2);
        if (jobInstances.size() < 2) {
            return null;
        }

        JobInstance previousJobInstance = jobInstances.get(1);
        List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(previousJobInstance);
        if (jobExecutions.isEmpty()) {
            return null;
        }

        JobExecution lastJobExecution = jobExecutions.get(0);
        if (lastJobExecution.getEndTime() == null) {
            return null;
        }
        return lastJobExecution.getEndTime();
    }


}
