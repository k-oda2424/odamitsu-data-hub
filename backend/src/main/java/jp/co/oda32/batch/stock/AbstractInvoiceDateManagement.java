package jp.co.oda32.batch.stock;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 棚卸日を管理する抽象クラス
 *
 * @author k_oda
 * @since 2021/08/02
 */
public class AbstractInvoiceDateManagement {

    public LocalDateTime getInventoryDateTime() {
        LocalDateTime now = LocalDateTime.now();
        if (now.getMonthValue() < 7) {
            return now.minusYears(1).withMonth(6).withDayOfMonth(21).truncatedTo(ChronoUnit.HOURS).minusSeconds(1);
        }
        return now.withMonth(6).withDayOfMonth(21).truncatedTo(ChronoUnit.DAYS).minusSeconds(1);
    }
}
