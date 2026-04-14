package jp.co.oda32.util.gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateTypeAdapter implements JsonDeserializer<LocalDate>, JsonSerializer<LocalDate> {
    private final DateTimeFormatter formatter;

    public LocalDateTypeAdapter() {
        this(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public LocalDateTypeAdapter(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public LocalDate deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        String value = json.getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, formatter);
        } catch (Exception e) {
            throw new JsonParseException("Failed to parse LocalDate: " + value, e);
        }
    }

    @Override
    public JsonElement serialize(LocalDate src, Type type, JsonSerializationContext context) {
        return new JsonPrimitive(src.format(formatter));
    }
}
