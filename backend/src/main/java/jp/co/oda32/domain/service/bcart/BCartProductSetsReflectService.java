package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartChangeHistory;
import jp.co.oda32.domain.repository.bcart.BCartChangeHistoryRepository;
import jp.co.oda32.dto.bcart.BCartReflectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B-CART商品セットの未反映変更を B-CART API へ PATCH 反映する Service。
 *
 * - 同期処理（バッチではなく REST 呼出から直接実行）
 * - aggregate 時に history ID リストを保持し、UPDATE は ID 限定で実行
 * - PATCH 失敗時は markReflected を呼ばず、再試行可能性を維持
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class BCartProductSetsReflectService {

    private static final List<String> TARGET_FIELDS = List.of("unit_price", "shipping_size");

    private final BCartChangeHistoryRepository historyRepository;
    private final BCartReflectTransactionService transactionService;

    @Qualifier("bCartHttpClient")
    private final OkHttpClient httpClient;

    public BCartReflectResult reflect(List<Long> productSetIds) {
        BCartReflectResult result = new BCartReflectResult();
        for (Long setId : productSetIds) {
            AggregatedChange agg = aggregateUnreflectedFields(setId);
            if (agg.fields().isEmpty()) {
                result.addSkipped(setId, "未反映の変更がありません");
                continue;
            }
            try {
                patchToBCart(setId, agg.fields());
                transactionService.markReflected(setId, agg.historyIds());
                result.addSuccess(setId);
            } catch (IOException e) {
                log.error("B-CART反映失敗 productSetId={}: {}", setId, e.getMessage());
                result.addFailure(setId, e.getMessage());
            } catch (RuntimeException e) {
                log.error("B-CART反映時に予期せぬエラー productSetId={}", setId, e);
                result.addFailure(setId, "予期せぬエラー: " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * 指定商品セットの未反映 history を集約。
     * 同一 field の複数行は ASC で並べた上で、最新値（最後の after_value）を採用。
     * 反映対象 history の ID リストも合わせて返す。
     */
    AggregatedChange aggregateUnreflectedFields(Long setId) {
        List<BCartChangeHistory> rows = historyRepository.findUnreflectedForProductSet(setId, TARGET_FIELDS);
        Map<String, String> latestAfter = new LinkedHashMap<>();
        List<Long> historyIds = new ArrayList<>();
        for (BCartChangeHistory h : rows) {
            latestAfter.put(h.getFieldName(), h.getAfterValue()); // ASC ソート済なので最後の値で上書きされる
            historyIds.add(h.getId());
        }
        return new AggregatedChange(latestAfter, historyIds);
    }

    private void patchToBCart(Long setId, Map<String, String> fields) throws IOException {
        FormBody.Builder fb = new FormBody.Builder(StandardCharsets.UTF_8);
        fields.forEach((k, v) -> {
            if (v != null) fb.add(k, v);
        });

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.bcart.jp")
                .addPathSegment("api")
                .addPathSegment("v1")
                .addPathSegment("product_sets")
                .addPathSegment(String.valueOf(setId))
                .build();

        Request req = new Request.Builder()
                .url(url)
                .patch(fb.build())
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
                .build();

        try (Response res = httpClient.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                String body = res.body() != null ? res.body().string() : "";
                throw new IOException("PATCH product_sets/" + setId + " failed: " + res.code() + " " + body);
            }
        }
    }

    public List<Long> findAllUnreflectedProductSetIds() {
        return historyRepository.findUnreflectedProductSetIds(TARGET_FIELDS);
    }

    public record AggregatedChange(Map<String, String> fields, List<Long> historyIds) {
    }
}
