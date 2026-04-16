package jp.co.oda32.batch.bcart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import jp.co.oda32.constant.BCartApiConfig;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.bcart.BCartProductSets;
import jp.co.oda32.domain.model.bcart.BCartProductSetsApiResponse;
import jp.co.oda32.domain.model.bcart.BCartVolumeDiscount;
import jp.co.oda32.domain.model.bcart.productSets.BCartGroupPrice;
import jp.co.oda32.domain.model.bcart.productSets.BCartSpecialPrice;
import jp.co.oda32.domain.model.bcart.productSets.StockParent;
import jp.co.oda32.domain.service.bcart.BCartGroupPriceService;
import jp.co.oda32.domain.service.bcart.BCartProductSetsService;
import jp.co.oda32.domain.service.bcart.BCartSpecialPriceService;
import jp.co.oda32.domain.service.bcart.BCartVolumeDiscountService;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.gson.CustomDateTypeAdapter;
import jp.co.oda32.util.gson.CustomNumberDeserializer;
import jp.co.oda32.util.gson.bcart.productSets.GroupPriceDeserializer;
import jp.co.oda32.util.gson.bcart.productSets.SpecialPriceDeserializer;
import jp.co.oda32.util.gson.bcart.productSets.StockParentDeserializer;
import jp.co.oda32.util.gson.bcart.productSets.VolumeDiscountDeserializer;
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
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * B-Cartシステムから商品セット情報を取り込むtasklet
 *
 * @author k_oda
 * @since 2023/04/21
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartProductSetsImportTasklet implements Tasklet {
    private final BCartProductSetsService bCartProductSetsService;
    private final BCartVolumeDiscountService bCartVolumeDiscountService;
    private final BCartSpecialPriceService bCartSpecialPriceService;
    private final BCartGroupPriceService bCartGroupPriceService;
    @Qualifier("bCartHttpClient")
    private final OkHttpClient httpClient;
    private final int API_LIMIT = 100;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        int i = 0;
        while (true) {
            try (Response response = executeBCartProductSetsAPI(i)) {
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
                        .registerTypeAdapter(Date.class, new CustomDateTypeAdapter("yyyy-MM-dd HH:mm:ss"))
                        .registerTypeAdapter(new TypeToken<Map<String, BCartSpecialPrice>>() {
                        }.getType(), new SpecialPriceDeserializer())
                        .registerTypeAdapter(new TypeToken<Map<String, BCartGroupPrice>>() {
                        }.getType(), new GroupPriceDeserializer())
                        .registerTypeAdapter(new TypeToken<Map<String, Integer>>() {
                        }.getType(), new VolumeDiscountDeserializer())
                        .registerTypeAdapter(StockParent.class, new StockParentDeserializer())
                        .create();

                BCartProductSetsApiResponse bCartProductSetsApiResponse = gson.fromJson(jsonResponse, BCartProductSetsApiResponse.class);
                List<BCartProductSets> bCartProductSetsList = bCartProductSetsApiResponse.getBCartProductSetsList();
                if (CollectionUtil.isEmpty(bCartProductSetsList)) {
                    // 空になったら終わり
                    break;
                }
                for (BCartProductSets bCartProductSets : bCartProductSetsList) {
                    // 数量割引テーブル登録
                    List<BCartVolumeDiscount> bCartVolumeDiscountList = saveBCartVolumeDiscount(bCartProductSets.getVolumeDiscountMap(), bCartProductSets.getId(), null);
                    // 特別価格テーブル登録
                    this.saveBCartSpecialPrice(bCartProductSets);
                    // グループ価格テーブル登録
                    this.saveBCartGroupPrice(bCartProductSets);
                    // セット商品登録
                    if (bCartVolumeDiscountList != null && !CollectionUtil.isEmpty(bCartVolumeDiscountList)) {
                        String volumeDiscountIds = bCartVolumeDiscountList.stream().map(BCartVolumeDiscount::getVolumeDiscountId).map(String::valueOf).collect(Collectors.joining(","));
                        bCartProductSets.setVolumeDiscountIds(volumeDiscountIds);
                    }
                    BCartProductSets existsSets = this.bCartProductSetsService.getByPK(bCartProductSets.getId());
                    if (existsSets != null && !existsSets.isBCartPriceReflected()) {
                        // 価格更新ができていない場合は、商品セットの通常価格を更新しない→単価を今の価格で上書きする
                        bCartProductSets.setUnitPrice(existsSets.getUnitPrice());
                    }
                    bCartProductSetsService.save(bCartProductSets);
                }
            } catch (IOException e) {
                log.error("Failed to execute BCartProductSetsApi: ", e);
                return RepeatStatus.CONTINUABLE;
            }
            i++;
        }
        return RepeatStatus.FINISHED;
    }

    private Response executeBCartProductSetsAPI(int i) throws IOException {
        OkHttpClient client = this.httpClient;
        int offset = API_LIMIT * i;
        HttpUrl url = new HttpUrl.Builder().scheme("https").host("api.bcart.jp").addPathSegment("api").addPathSegment("v1").addPathSegment("product_sets").addQueryParameter("limit", String.valueOf(API_LIMIT)).addQueryParameter("offset", String.valueOf(offset)).build();

        Request request = new Request.Builder().url(url).get().addHeader("Accept", "application/json").addHeader("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken()).build();

        return client.newCall(request).execute();
    }

    private List<BCartVolumeDiscount> saveBCartVolumeDiscount(Map<String, BigDecimal> volumeDiscountMap, Long productSetId, Long customerId) {
        if (volumeDiscountMap == null || volumeDiscountMap.isEmpty()) {
            return null;
        }
        List<BCartVolumeDiscount> volumeDiscountList = this.bCartVolumeDiscountService.findByProductSetIdAndCustomerId(productSetId, customerId);
        if (CollectionUtil.isEmpty(volumeDiscountList)) {
            // 初回登録
            List<BCartVolumeDiscount> newVolumeDiscounts = volumeDiscountMap.entrySet().stream().map(entry -> BCartVolumeDiscount.builder()
                    .productSetId(productSetId)
                    .customerId(customerId)
                    .setNum(new BigDecimal(entry.getKey()))
                    .unitPrice(entry.getValue())
                    .bCartPriceReflected(true)
                    .build()).collect(Collectors.toList());
            return bCartVolumeDiscountService.saveAll(newVolumeDiscounts);
        }
        // 更新
        // 一旦全ての削除フラグを立てる
        volumeDiscountList = volumeDiscountList.stream().peek(bCartVolumeDiscount -> bCartVolumeDiscount.setDelFlg(Flag.YES.getValue())).collect(Collectors.toList());

        List<BCartVolumeDiscount> insertList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> volumeDiscountEntry : volumeDiscountMap.entrySet()) {
            BigDecimal importSetNum = new BigDecimal(volumeDiscountEntry.getKey());
            BigDecimal importUnitPrice = volumeDiscountEntry.getValue();
            if (volumeDiscountList.stream().noneMatch(bCartVolumeDiscount -> importSetNum.compareTo(bCartVolumeDiscount.getSetNum()) == 0)) {
                // volumeDiscountMapにしかないものを登録する
                BCartVolumeDiscount newVolumeDiscount = BCartVolumeDiscount.builder()
                        .productSetId(productSetId)
                        .customerId(customerId)
                        .setNum(importSetNum)
                        .unitPrice(importUnitPrice)
                        .build();
                insertList.add(newVolumeDiscount);
            }
            volumeDiscountList = volumeDiscountList.stream()
                    // 更新対象とするのはb-cartシステムに最新価格を反映している行のみ
                    .filter(BCartVolumeDiscount::isBCartPriceReflected)
                    .filter(bCartVolumeDiscount -> importSetNum.compareTo(bCartVolumeDiscount.getSetNum()) == 0)
                    .peek(bCartVolumeDiscount -> bCartVolumeDiscount.setDelFlg(Flag.NO.getValue()))
                    .peek(bCartVolumeDiscount -> bCartVolumeDiscount.setUnitPrice(importUnitPrice))
                    .collect(Collectors.toList());
        }
        // DBに登録・更新
        volumeDiscountList = bCartVolumeDiscountService.saveAll(volumeDiscountList);
        insertList = bCartVolumeDiscountService.saveAll(insertList);
        volumeDiscountList.addAll(insertList);
        return volumeDiscountList;
    }

    private void saveBCartSpecialPrice(BCartProductSets bCartProductSets) {
        // 配列のキー=会員ID,unit_price=価格,volume_discount=数量割引（配列のキー=セット数,値=価格）
        Map<String, BCartSpecialPrice> specialPriceMap = bCartProductSets.getSpecialPriceMap();
        if (specialPriceMap == null || specialPriceMap.isEmpty()) {
            return;
        }
        List<BCartSpecialPrice> importSpecialPriceList = this.convertSpecialPriceMap(specialPriceMap, bCartProductSets.getId());
        List<BCartSpecialPrice> bCartSpecialPriceList = this.bCartSpecialPriceService.findByProductSetId(bCartProductSets.getId());
        if (CollectionUtil.isEmpty(bCartSpecialPriceList)) {
            // 初回登録
            for (BCartSpecialPrice bCartSpecialPrice : importSpecialPriceList) {
                bCartSpecialPrice.setBCartPriceReflected(true);
                this.bCartSpecialPriceService.save(bCartSpecialPrice);
            }
            return;
        }
        // 更新処理
        List<BCartSpecialPrice> updatedSpecialPriceList = new ArrayList<>();
        List<BCartSpecialPrice> deletedSpecialPriceList = new ArrayList<>();
        List<String> importSpecialPricePriceKeyList = importSpecialPriceList.stream().map(spe -> String.format("%s_%s", spe.getProductSetId(), spe.getCustomerId())).collect(Collectors.toList());
        for (BCartSpecialPrice existingSpecialPrice : bCartSpecialPriceList) {
            if (!existingSpecialPrice.isBCartPriceReflected()) {
                // 本システムからb-cartシステムに価格をまだ反映していない場合はスキップする
                continue;
            }
            String pk = String.format("%s_%s", existingSpecialPrice.getProductSetId(), existingSpecialPrice.getCustomerId());
            if (importSpecialPricePriceKeyList.contains(pk)) {
                BCartSpecialPrice updateSpecialPriceData = importSpecialPriceList.stream()
                        .filter(importData -> importData.getProductSetId().equals(existingSpecialPrice.getProductSetId()))
                        .filter(importData -> importData.getCustomerId().equals(existingSpecialPrice.getCustomerId()))
                        .findFirst().orElse(null);
                if (updateSpecialPriceData == null) {
                    // ありえないけど一応
                    continue;
                }
                existingSpecialPrice.setUnitPrice(updateSpecialPriceData.getUnitPrice());
//                existingSpecialPrice.setVolumeDiscountIdList(convertVolumeDiscountMapToVolumeDiscountIdList(updateSpecialPriceData.getVolumeDiscountMap(), bCartProductSets.getId(), updateSpecialPriceData.getCustomerId()));
                existingSpecialPrice.setVolumeDiscountIds(convertVolumeDiscountMapToVolumeDiscountIds(updateSpecialPriceData.getVolumeDiscountMap(), bCartProductSets.getId(), updateSpecialPriceData.getCustomerId()));
                updatedSpecialPriceList.add(existingSpecialPrice);
            } else {
                deletedSpecialPriceList.add(existingSpecialPrice);
            }
        }
        // Save updated
        this.bCartSpecialPriceService.saveAll(updatedSpecialPriceList);

        // Delete entries not present in the new data
        this.bCartSpecialPriceService.deleteAll(deletedSpecialPriceList);
    }

    private List<BCartSpecialPrice> convertSpecialPriceMap(Map<String, BCartSpecialPrice> specialPriceMap, Long productSetId) {
        List<BCartSpecialPrice> specialPriceList = new ArrayList<>();
        for (Map.Entry<String, BCartSpecialPrice> entry : specialPriceMap.entrySet()) {
            // 配列のキーの会員IDはカンマ区切りで複数の会員IDがある
            List<Long> customerIdList = Arrays.stream(entry.getKey().split(",")).map(Long::new).collect(Collectors.toList());
            for (Long customerId : customerIdList) {
                specialPriceList.add(BCartSpecialPrice.builder()
                        .productSetId(productSetId)
                        .customerId(customerId)
                        .unitPrice(entry.getValue().getUnitPrice())
                        .volumeDiscountIds(convertVolumeDiscountMapToVolumeDiscountIds(entry.getValue().getVolumeDiscountMap(), productSetId, entry.getValue().getCustomerId()))
                        .build());
            }
        }
        return specialPriceList;
    }

    private String convertVolumeDiscountMapToVolumeDiscountIds(Map<String, BigDecimal> volumeDiscountMap, Long productSetId, Long customerId) {
        List<BCartVolumeDiscount> bCartVolumeDiscountList = saveBCartVolumeDiscount(volumeDiscountMap, productSetId, customerId);
        if (bCartVolumeDiscountList == null || CollectionUtil.isEmpty(bCartVolumeDiscountList)) {
            return null;
        }
        return bCartVolumeDiscountList.stream().map(BCartVolumeDiscount::getVolumeDiscountId).map(String::valueOf).collect(Collectors.joining(","));
    }

    private void saveBCartGroupPrice(BCartProductSets bCartProductSets) {
        // 配列のキー=グループID, name=グループ名, rate=割引率, unit_price=単価, fixed_price=固定価格, volume_discount=数量割引（配列のキー=セット数,値=価格）
        Map<String, BCartGroupPrice> groupPriceMap = bCartProductSets.getGroupPriceMap();
        if (groupPriceMap == null || groupPriceMap.isEmpty()) {
            return;
        }
        List<BCartGroupPrice> bCartGroupPriceList = this.bCartGroupPriceService.findByProductSetId(bCartProductSets.getId());
        if (CollectionUtil.isEmpty(bCartGroupPriceList)) {
            // 初回登録
            List<BCartGroupPrice> newGroupPriceList = groupPriceMap.entrySet().stream().map(entry -> BCartGroupPrice.builder()
                    .productSetId(bCartProductSets.getId())
                    .groupId(entry.getKey())
                    .name(entry.getValue().getName())
                    .rate(entry.getValue().getRate())
                    .unitPrice(entry.getValue().getUnitPrice())
                    .fixedPrice(entry.getValue().getFixedPrice())
                    .volumeDiscountIds(convertVolumeDiscountMapToVolumeDiscountIds(entry.getValue().getVolumeDiscount(), bCartProductSets.getId(), null))
                    .bCartPriceReflected(true)
                    .build()).collect(Collectors.toList());

            this.bCartGroupPriceService.saveAll(newGroupPriceList);
            return;
        }

        // 更新処理
        List<BCartGroupPrice> updatedGroupPriceList = new ArrayList<>();
        List<BCartGroupPrice> deletedGroupPriceList = new ArrayList<>();

        for (BCartGroupPrice existingGroupPrice : bCartGroupPriceList) {
            if (!existingGroupPrice.isBCartPriceReflected()) {
                // 本システムからb-cartシステムに価格をまだ反映していない場合はスキップする
                continue;
            }
            String groupIdKey = existingGroupPrice.getGroupId();
            if (groupPriceMap.containsKey(groupIdKey)) {
                BCartGroupPrice newGroupPriceData = groupPriceMap.get(groupIdKey);
                existingGroupPrice.setName(newGroupPriceData.getName());
                existingGroupPrice.setRate(newGroupPriceData.getRate());
                existingGroupPrice.setUnitPrice(newGroupPriceData.getUnitPrice());
                existingGroupPrice.setFixedPrice(newGroupPriceData.getFixedPrice());
                existingGroupPrice.setVolumeDiscountIds(convertVolumeDiscountMapToVolumeDiscountIds(newGroupPriceData.getVolumeDiscount(), bCartProductSets.getId(), null));
                updatedGroupPriceList.add(existingGroupPrice);
                // Remove matched entry from groupPriceMap
                groupPriceMap.remove(groupIdKey);
            } else {
                deletedGroupPriceList.add(existingGroupPrice);
            }
        }
        // Save updated entries
        this.bCartGroupPriceService.saveAll(updatedGroupPriceList);
        // Delete entries not present in the new data
        this.bCartGroupPriceService.deleteAll(deletedGroupPriceList);
    }
}
