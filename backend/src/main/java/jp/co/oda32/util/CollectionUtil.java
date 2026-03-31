package jp.co.oda32.util;

import java.util.Collection;

public final class CollectionUtil {

    private CollectionUtil() {}

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }
}
