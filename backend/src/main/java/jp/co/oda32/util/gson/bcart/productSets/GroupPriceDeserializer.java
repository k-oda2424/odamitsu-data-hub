package jp.co.oda32.util.gson.bcart.productSets;

import com.google.gson.*;
import jp.co.oda32.domain.model.bcart.productSets.BCartGroupPrice;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Log4j2
public class GroupPriceDeserializer implements JsonDeserializer<Map<String, BCartGroupPrice>> {

    @Override
    public Map<String, BCartGroupPrice> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        Map<String, BCartGroupPrice> groupPriceMap = new HashMap<>();

        for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            try {
                JsonObject groupPriceObject = entry.getValue().getAsJsonObject();

                String name = groupPriceObject.has("name") ? groupPriceObject.get("name").getAsString() : null;
                String rate = groupPriceObject.has("rate") ? groupPriceObject.get("rate").getAsString() : null;
                BigDecimal unitPrice = groupPriceObject.has("unit_price") ? groupPriceObject.get("unit_price").getAsBigDecimal() : null;
                JsonElement fixedPriceJson = groupPriceObject.get("fixed_price");
                BigDecimal fixedPrice = fixedPriceJson != null && !fixedPriceJson.isJsonNull() ? BigDecimal.valueOf(fixedPriceJson.getAsDouble()) : null;


                JsonElement volumeDiscountJson = groupPriceObject.get("volume_discount");
                Map<String, BigDecimal> volumeDiscount = null;
                if (volumeDiscountJson != null && !volumeDiscountJson.isJsonNull()) {
                    JsonObject volumeDiscountObj = volumeDiscountJson.getAsJsonObject();
                    volumeDiscount = new HashMap<>();
                    for (Map.Entry<String, JsonElement> volumeDiscountEntry : volumeDiscountObj.entrySet()) {
                        volumeDiscount.put(volumeDiscountEntry.getKey(), volumeDiscountEntry.getValue().getAsBigDecimal());
                    }
                }
                BigDecimal rateB = rate != null ? new BigDecimal(rate) : null;
                BCartGroupPrice bCartGroupPrice = BCartGroupPrice.builder()
                        .name(name)
                        .rate(rateB)
                        .unitPrice(unitPrice)
                        .fixedPrice(fixedPrice)
                        .volumeDiscount(volumeDiscount)
                        .build();

                groupPriceMap.put(entry.getKey(), bCartGroupPrice);
            } catch (ClassCastException classCastException) {
                log.error(classCastException.getLocalizedMessage());
            }
        }
        return groupPriceMap;
    }
}
