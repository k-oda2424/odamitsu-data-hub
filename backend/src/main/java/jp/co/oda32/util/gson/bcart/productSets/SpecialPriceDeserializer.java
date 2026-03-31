package jp.co.oda32.util.gson.bcart.productSets;

import com.google.gson.*;
import jp.co.oda32.domain.model.bcart.productSets.BCartSpecialPrice;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class SpecialPriceDeserializer implements JsonDeserializer<Map<String, BCartSpecialPrice>> {

    @Override
    public Map<String, BCartSpecialPrice> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        Map<String, BCartSpecialPrice> resultMap = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            BCartSpecialPrice BCartSpecialPrice = context.deserialize(entry.getValue(), BCartSpecialPrice.class);
            resultMap.put(entry.getKey(), BCartSpecialPrice);
        }

        return resultMap;
    }
}
