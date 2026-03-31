package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.domain.model.bcart.BCartProductSets;
import jp.co.oda32.domain.model.bcart.BCartVolumeDiscount;
import jp.co.oda32.domain.model.bcart.productSets.BCartGroupPrice;
import jp.co.oda32.domain.model.bcart.productSets.BCartSpecialPrice;
import jp.co.oda32.domain.service.bcart.BCartGroupPriceService;
import jp.co.oda32.domain.service.bcart.BCartProductSetsService;
import jp.co.oda32.domain.service.bcart.BCartSpecialPriceService;
import jp.co.oda32.domain.service.bcart.BCartVolumeDiscountService;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * B-Cart商品価格更新タスクレット
 *
 * @author k_oda
 * @since 2023/05/29
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartGoodsPriceUpdateTasklet implements Tasklet {
    private final BCartProductSetsService bCartProductSetsService;
    private final BCartVolumeDiscountService bCartVolumeDiscountService;
    private final BCartGroupPriceService bCartGroupPriceService;
    private final BCartSpecialPriceService bCartSpecialPriceService;
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        // 更新対象を取得
        List<BCartProductSets> targetList = bCartProductSetsService.findNotBCartPriceReflected();

        // 商品価格更新APIを呼び出す
        int count = 0;
        for (BCartProductSets target : targetList) {
            List<BCartGroupPrice> groupPriceList = target.getGroupPrices();
            List<BCartSpecialPrice> specialPriceList = target.getSpecialPrices();
            List<Long> volumeDiscountIdList;
            List<BCartVolumeDiscount> volumeDiscountList = null;
            if (!StringUtil.isEmpty(target.getVolumeDiscountIds())) {
                volumeDiscountIdList = Arrays.stream(target.getVolumeDiscountIds().split(","))
                        .map(Long::valueOf)
                        .collect(Collectors.toList());
                volumeDiscountList = this.bCartVolumeDiscountService.findByVolumeDiscountIdList(volumeDiscountIdList);
            }
            if (++count % 250 == 0) {
                // 5分300回制限があるため250件ずつに分割(他のAPIが叩けなくなるため)
                // 250件ごとに一定時間待機
                Thread.sleep(1000 * 60 * 5); // 5分待機
            }
            if (updateProductPrices(target.getId(), target.getUnitPrice(), target.getPurchasePrice(), groupPriceList, specialPriceList, volumeDiscountList)) {
                // 反映完了フラグを立てる
                updateBCartPriceReflected(target, volumeDiscountList);
            }
        }
        return RepeatStatus.FINISHED;
    }

    /**
     * 商品価格更新APIを叩きます
     *
     * @param setId              b-cartの商品セットID
     * @param newUnitPrice       標準単価
     * @param groupPriceList     グループ価格のリスト
     * @param specialPriceList   特価のリスト
     * @param volumeDiscountList 数量割引のリスト
     * @throws Exception 例外
     */
    public boolean updateProductPrices(Long setId, BigDecimal newUnitPrice, BigDecimal purchasePrice, List<BCartGroupPrice> groupPriceList, List<BCartSpecialPrice> specialPriceList, List<BCartVolumeDiscount> volumeDiscountList) throws Exception {
        String url = "https://api.bcart.jp/api/v1/product_sets/" + setId;

        // リクエストボディを作成
        JSONObject bodyJson = new JSONObject();
        bodyJson.put("unit_price", newUnitPrice);

        // グループ価格(グループ個別価格設定に入ってしまうため、わざわざ設定しない)
//        JSONObject groupPriceJson = new JSONObject();
//        for (BCartGroupPrice groupPrice : groupPriceList) {
//            JSONObject priceJson = new JSONObject();
//            priceJson.put("fixed_price", groupPrice.getUnitPrice());
//            groupPriceJson.put(groupPrice.getGroupId(), priceJson);
//        }
//        bodyJson.put("group_price", groupPriceJson);

        // 特別価格
        JSONObject specialPriceJson = new JSONObject();
        for (BCartSpecialPrice specialPrice : specialPriceList) {
            JSONObject priceJson = new JSONObject();
            priceJson.put("unit_price", specialPrice.getUnitPrice());
            specialPriceJson.put(String.valueOf(specialPrice.getCustomerId()), priceJson);
        }
        bodyJson.put("special_price", specialPriceJson);

        // 数量割引
        JSONObject volumeDiscountJson = new JSONObject();
        if (!CollectionUtil.isEmpty(volumeDiscountList)) {
            for (BCartVolumeDiscount volumeDiscount : volumeDiscountList) {
                volumeDiscountJson.put(volumeDiscount.getSetNum().toString(), volumeDiscount.getUnitPrice());
            }
            bodyJson.put("volume_discount", volumeDiscountJson);
        }
        if (purchasePrice != null) {
            JSONArray customsArray = new JSONArray();
            JSONObject customFieldJson = new JSONObject();
            customFieldJson.put("field_id", 9);
            customFieldJson.put("value", purchasePrice.toString());
            customsArray.put(customFieldJson);
            bodyJson.put("customs", customsArray);
        }
        // リクエストを作成
        RequestBody body = RequestBody.create(bodyJson.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
                .header("Content-Type", "application/json")
                .patch(body) // PATCHリクエストを使用
                .build();
        log.info(request.body());
        // リクエストを送信
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("response:" + response);
                return false;
            }
            log.info("response:" + response);
            // レスポンスを処理
            return true;
        }
    }

    private void updateBCartPriceReflected(BCartProductSets target, List<BCartVolumeDiscount> volumeDiscountList) {
        try {
            if (!CollectionUtil.isEmpty(volumeDiscountList)) {
                for (BCartVolumeDiscount bCartVolumeDiscount : volumeDiscountList) {
                    bCartVolumeDiscount.setBCartPriceReflected(true);
                    this.bCartVolumeDiscountService.save(bCartVolumeDiscount);
                }
            }
            List<BCartGroupPrice> groupPriceList = target.getGroupPrices();
            if (!CollectionUtil.isEmpty(groupPriceList)) {
                for (BCartGroupPrice bCartGroupPrice : groupPriceList) {
                    bCartGroupPrice.setBCartPriceReflected(true);
                    this.bCartGroupPriceService.save(bCartGroupPrice);
                }
            }

            List<BCartSpecialPrice> specialPriceList = target.getSpecialPrices();
            if (!CollectionUtil.isEmpty(specialPriceList)) {
                for (BCartSpecialPrice bCartSpecialPrice : specialPriceList) {
                    bCartSpecialPrice.setBCartPriceReflected(true);
                    this.bCartSpecialPriceService.save(bCartSpecialPrice);
                }
            }
            // 全て更新が成功した場合、商品セットテーブルもtrueにする
            this.bCartProductSetsService.updateBCartPriceReflected(target);
        } catch (Exception e) {
            log.error(String.format("B-Cart価格更新バッチでDB更新時にエラーが発生しました。エラーメッセージ:%s", e.getMessage()));
        }
    }
}
