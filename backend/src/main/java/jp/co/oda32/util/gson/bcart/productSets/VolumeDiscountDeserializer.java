package jp.co.oda32.util.gson.bcart.productSets;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class VolumeDiscountDeserializer implements JsonDeserializer<Map<String, Integer>> {

    @Override
    public Map<String, Integer> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        Map<String, Integer> volumeDiscount = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            volumeDiscount.put(entry.getKey(), entry.getValue().getAsInt());
        }

        return volumeDiscount;
    }
}
