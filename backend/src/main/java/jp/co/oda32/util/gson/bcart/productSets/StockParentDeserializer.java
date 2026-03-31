package jp.co.oda32.util.gson.bcart.productSets;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import jp.co.oda32.domain.model.bcart.productSets.StockParent;

import java.lang.reflect.Type;
import java.util.Map;

public class StockParentDeserializer implements JsonDeserializer<StockParent> {

    @Override
    public StockParent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        Map<String, Integer> stockParentMap = context.deserialize(jsonObject, new TypeToken<Map<String, Integer>>() {
        }.getType());

        StockParent stockParent = new StockParent();
        stockParent.setStockParentMap(stockParentMap);

        return stockParent;
    }
}
