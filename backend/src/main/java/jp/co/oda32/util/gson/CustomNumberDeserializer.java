package jp.co.oda32.util.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class CustomNumberDeserializer<T extends Number> extends TypeAdapter<T> {

    private final Class<T> type;

    public CustomNumberDeserializer(Class<T> type) {
        this.type = type;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value);
    }

    @Override
    public T read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String stringValue = in.nextString();
        if (stringValue.isEmpty()) {
            return null;
        }
        try {
            if (type == Integer.class) {
                return type.cast(Integer.valueOf(stringValue));
            } else if (type == Double.class) {
                return type.cast(Double.valueOf(stringValue));
            } else if (type == Float.class) {
                return type.cast(Float.valueOf(stringValue));
            } else if (type == Long.class) {
                return type.cast(Long.valueOf(stringValue));
            } else {
                throw new RuntimeException("Unexpected number type: " + type);
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("Failed to parse number: " + stringValue, e);
        }
    }
}
