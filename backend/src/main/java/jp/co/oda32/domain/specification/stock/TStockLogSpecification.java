package jp.co.oda32.domain.specification.stock;

import jp.co.oda32.constant.StockLogReason;
import jp.co.oda32.domain.model.stock.TStockLog;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 在庫履歴検索条件
 *
 * @author k_oda
 * @since 2019/04/23
 */
public class TStockLogSpecification extends CommonSpecification<TStockLog> {
    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<TStockLog> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 倉庫番号の検索条件
     *
     * @param warehouseNo 倉庫番号
     * @return 倉庫番号の検索条件
     */
    public Specification<TStockLog> warehouseNoContains(Integer warehouseNo) {
        return warehouseNo == null ? null : (root, query, cb) -> cb.equal(root.get("warehouseNo"), warehouseNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TStockLog> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }
    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    public Specification<TStockLog> goodsNameContains(String goodsName) {
        return likeNormalized("mGoods", "goodsName", goodsName);
    }

    /**
     * 移動理由の検索条件
     *
     * @param reason 移動理由
     * @return 移動理由の検索条件
     */
    private Specification<TStockLog> reasonContains(StockLogReason reason) {
        return reason == null ? null : (root, query, cb) -> cb.equal(root.get("reason"), reason);
    }

    /**
     * 移動理由の複数検索条件(or接続)
     *
     * @param reasonList 移動理由の配列
     * @return 商品名の複数検索条件
     */
    public Specification<TStockLog> reasonListContains(List<StockLogReason> reasonList) {
        final Specification<TStockLog> reasonSpecification = Specification.where(null);
        return reasonList.stream()
                .map(this::reasonContains)
                .reduce(reasonSpecification, Specification::or);
    }

    /**
     * 在庫移動時刻の検索条件
     *
     * @param moveTimeFrom 在庫移動時刻FROM
     * @param moveTimeTo   在庫移動時刻TO
     * @return 在庫移動時刻の検索条件
     */
    public Specification<TStockLog> moveTimeContains(LocalDateTime moveTimeFrom, LocalDateTime moveTimeTo) {
        if (moveTimeFrom == null && moveTimeTo == null) {
            return null;
        }
        if (moveTimeTo == null) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("moveTime"), moveTimeFrom);
        }
        if (moveTimeFrom == null) {
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("moveTime"), moveTimeTo);
        }
        return (root, query, cb) -> cb.between(root.get("moveTime"), moveTimeFrom, moveTimeTo);
    }
}
