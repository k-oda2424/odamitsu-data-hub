package jp.co.oda32.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeUtilTest {

    @Test
    void localDateToSlipDate_yyyyMMdd() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        assertEquals("20240115", DateTimeUtil.localDateToSlipDate(date));
    }

    @Test
    void displayDate_slashFormat() {
        LocalDate date = LocalDate.of(2024, 3, 5);
        assertEquals("2024/03/05", DateTimeUtil.displayDate(date));
    }

    @Test
    void stringToLocalDate_slashFormat() {
        LocalDate result = DateTimeUtil.stringToLocalDate("2024/01/15");
        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 15), result);
    }

    @Test
    void stringToLocalDate_null_returnsNull() {
        LocalDate result = DateTimeUtil.stringToLocalDate(null);
        assertNull(result);
    }

    @Test
    void stringToLocalDate_empty_returnsNull() {
        LocalDate result = DateTimeUtil.stringToLocalDate("");
        assertNull(result);
    }

    @Test
    void stringHaifunToLocalDate_valid() {
        LocalDate result = DateTimeUtil.stringHaifunToLocalDate("2024-01-15");
        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 15), result);
    }

    @Test
    void localDateTimeToSlipDate_valid() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
        assertEquals("20240615", DateTimeUtil.localDateTimeToSlipDate(dateTime));
    }

    @Test
    void localDateTimeToSlipDate_null_returnsEmpty() {
        assertEquals("", DateTimeUtil.localDateTimeToSlipDate(null));
    }

    @Test
    void localDateToSlipDate_null_returnsEmpty() {
        assertEquals("", DateTimeUtil.localDateToSlipDate(null));
    }

    @Test
    void displayDate_null_returnsEmpty() {
        assertEquals("", DateTimeUtil.displayDate((LocalDate) null));
    }

    @Test
    void displayJpnDate_valid() {
        LocalDate date = LocalDate.of(2024, 3, 5);
        assertEquals("2024年03月05日", DateTimeUtil.displayJpnDate(date));
    }

    @Test
    void lastDayOfMonth_valid() {
        LocalDate date = LocalDate.of(2024, 2, 10);
        assertEquals(LocalDate.of(2024, 2, 29), DateTimeUtil.lastDayOfMonth(date));
    }

    @Test
    void isRange_withinRange() {
        LocalDate check = LocalDate.of(2024, 6, 15);
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        assertTrue(DateTimeUtil.isRange(check, from, to));
    }

    @Test
    void isRange_nullCheckDate() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        assertFalse(DateTimeUtil.isRange(null, from, to));
    }
}
