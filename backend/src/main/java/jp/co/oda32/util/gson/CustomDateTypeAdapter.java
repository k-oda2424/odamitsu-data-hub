package jp.co.oda32.util.gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CustomDateTypeAdapter implements JsonDeserializer<Date> {
    private final DateFormat dateFormat;

    public CustomDateTypeAdapter(String format) {
        dateFormat = new SimpleDateFormat(format);
    }

    @Override
    public Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        try {
            synchronized (dateFormat) {
                return dateFormat.parse(jsonElement.getAsString());
            }
        } catch (ParseException e) {
            throw new JsonParseException(e);
        }
    }
}
