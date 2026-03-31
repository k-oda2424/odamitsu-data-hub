package jp.co.oda32.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {

    @Test
    void isEmpty_null() {
        assertTrue(StringUtil.isEmpty(null));
    }

    @Test
    void isEmpty_empty() {
        assertTrue(StringUtil.isEmpty(""));
    }

    @Test
    void isEmpty_notEmpty() {
        assertFalse(StringUtil.isEmpty("test"));
    }

    @Test
    void isEqual_same() {
        assertTrue(StringUtil.isEqual("abc", "abc"));
    }

    @Test
    void isEqual_different() {
        assertFalse(StringUtil.isEqual("abc", "def"));
    }

    @Test
    void isNotEmpty_withValue() {
        assertTrue(StringUtil.isNotEmpty("test"));
    }

    @Test
    void isNotEmpty_null() {
        assertFalse(StringUtil.isNotEmpty(null));
    }
}
