package jp.co.oda32.util.gson;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class GsonJsonElementType implements UserType<JsonElement> {
    private final Gson gson = new Gson();

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<JsonElement> returnedClass() {
        return JsonElement.class;
    }

    @Override
    public boolean equals(JsonElement x, JsonElement y) {
        return x != null ? x.equals(y) : y == null;
    }

    @Override
    public int hashCode(JsonElement x) {
        return x != null ? x.hashCode() : 0;
    }

    @Override
    public JsonElement nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        String json = rs.getString(position);
        return json != null ? gson.fromJson(json, JsonElement.class) : null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, JsonElement value, int index, SharedSessionContractImplementor session) throws SQLException {
        String json = value != null ? gson.toJson(value) : null;
        st.setObject(index, json, Types.OTHER);
    }

    @Override
    public JsonElement deepCopy(JsonElement value) {
        return value != null ? gson.fromJson(gson.toJson(value), JsonElement.class) : null;
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(JsonElement value) {
        return value != null ? gson.toJson(value) : null;
    }

    @Override
    public JsonElement assemble(Serializable cached, Object owner) {
        return cached != null ? gson.fromJson((String) cached, JsonElement.class) : null;
    }
}
