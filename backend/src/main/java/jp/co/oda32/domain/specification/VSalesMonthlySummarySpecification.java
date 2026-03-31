package jp.co.oda32.domain.specification;

import jp.co.oda32.domain.model.VSalesMonthlySummary;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * 月間売上マテリアライズドViewの検索条件
 *
 * @author k_oda
 * @since 2020/03/13
 */
public class VSalesMonthlySummarySpecification {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<VSalesMonthlySummary> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 売上月の検索条件
     *
     * @param month 売上月(YYYY-MM)
     * @return 売上月の検索条件
     */
    public Specification<VSalesMonthlySummary> monthContains(LocalDate month) {
        if (month == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);
        String monthStr = month.format(formatter);
        return (root, query, cb) -> cb.equal(root.get("month"), monthStr);
    }

    /**
     * 売上月の検索条件
     *
     * @param dateFrom 売上月FROM
     * @param dateTo   売上月TO
     * @return 売上月FROMの検索条件
     */
    public Specification<VSalesMonthlySummary> monthRangeContains(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);
        if (dateTo == null) {
            String dateFromStr = dateFrom.format(formatter);
            // orderDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("month"), dateFromStr);
        }
        if (dateFrom == null) {
            String dateToStr = dateTo.format(formatter);
            // orderDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("month"), dateToStr);
        }
        String dateFromStr = dateFrom.format(formatter);
        String dateToStr = dateTo.format(formatter);
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("month"), dateFromStr, dateToStr);
    }
}
