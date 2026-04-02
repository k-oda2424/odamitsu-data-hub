package jp.co.oda32.api.purchase;

import jp.co.oda32.dto.purchase.SupplierQuoteDataResponse;
import jp.co.oda32.dto.purchase.SupplierQuoteHistoryResponse;
import jp.co.oda32.util.StringUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/supplier-quote-data")
@RequiredArgsConstructor
public class SupplierQuoteDataController {

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping
    public ResponseEntity<List<SupplierQuoteDataResponse>> list(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String goodsName) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT ON (d.jan_code) ");
        sql.append("d.jan_code, d.quote_goods_name, d.specification, d.quantity_per_case, ");
        sql.append("d.new_price, d.new_box_price, ");
        sql.append("h.effective_date, h.supplier_name, h.supplier_code, d.quote_import_detail_id ");
        sql.append("FROM t_quote_import_detail d ");
        sql.append("JOIN t_quote_import_header h ON d.quote_import_id = h.quote_import_id ");
        sql.append("WHERE h.del_flg = '0' AND h.shop_no = :shopNo AND d.jan_code IS NOT NULL");

        if (StringUtil.isNotEmpty(supplierCode)) {
            sql.append(" AND h.supplier_code = :supplierCode");
        }
        if (StringUtil.isNotEmpty(goodsName)) {
            sql.append(" AND nfkc(d.quote_goods_name) LIKE '%' || :goodsName || '%'");
        }

        sql.append(" ORDER BY d.jan_code, h.effective_date DESC, d.quote_import_detail_id DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("shopNo", shopNo);

        if (StringUtil.isNotEmpty(supplierCode)) {
            query.setParameter("supplierCode", supplierCode);
        }
        if (StringUtil.isNotEmpty(goodsName)) {
            query.setParameter("goodsName", StringUtil.normalizeForSearch(goodsName));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<SupplierQuoteDataResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(SupplierQuoteDataResponse.builder()
                    .janCode((String) row[0])
                    .quoteGoodsName((String) row[1])
                    .specification((String) row[2])
                    .quantityPerCase(toInteger(row[3]))
                    .currentPrice(toBigDecimal(row[4]))
                    .currentBoxPrice(toBigDecimal(row[5]))
                    .effectiveDate(toLocalDate(row[6]))
                    .supplierName((String) row[7])
                    .supplierCode((String) row[8])
                    .quoteImportDetailId(toInteger(row[9]))
                    .build());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<List<SupplierQuoteHistoryResponse>> history(
            @RequestParam Integer shopNo,
            @RequestParam String janCode) {

        String sql = "SELECT d.quote_import_detail_id, h.quote_date, h.effective_date, "
                + "d.old_price, d.new_price, d.old_box_price, d.new_box_price, "
                + "h.file_name, h.change_reason, h.supplier_name "
                + "FROM t_quote_import_detail d "
                + "JOIN t_quote_import_header h ON d.quote_import_id = h.quote_import_id "
                + "WHERE h.del_flg = '0' AND h.shop_no = :shopNo AND d.jan_code = :janCode "
                + "ORDER BY h.effective_date DESC, d.quote_import_detail_id DESC";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("shopNo", shopNo);
        query.setParameter("janCode", janCode);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<SupplierQuoteHistoryResponse> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            result.add(SupplierQuoteHistoryResponse.builder()
                    .quoteImportDetailId(toInteger(row[0]))
                    .quoteDate(toLocalDate(row[1]))
                    .effectiveDate(toLocalDate(row[2]))
                    .oldPrice(toBigDecimal(row[3]))
                    .newPrice(toBigDecimal(row[4]))
                    .oldBoxPrice(toBigDecimal(row[5]))
                    .newBoxPrice(toBigDecimal(row[6]))
                    .fileName((String) row[7])
                    .changeReason((String) row[8])
                    .supplierName((String) row[9])
                    .latest(i == 0)
                    .build());
        }

        return ResponseEntity.ok(result);
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return ((Number) value).intValue();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
