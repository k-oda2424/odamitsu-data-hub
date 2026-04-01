package jp.co.oda32.domain.specification.estimate;

import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積テーブル検索条件
 *
 * @author k_oda
 * @since 2022/10/24
 */
public class TEstimateSpecification extends CommonSpecification<TEstimate> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo ショップ番号
     * @return ショップ番号の検索条件
     */
    public Specification<TEstimate> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 見積番号の検索条件
     *
     * @param estimateNo 見積番号
     * @return 見積番号の検索条件
     */
    public Specification<TEstimate> estimateNoContains(Integer estimateNo) {
        return estimateNo == null ? null : (root, query, cb) -> cb.equal(root.get("estimateNo"), estimateNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TEstimate> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 得意先番号の検索条件
     *
     * @param partnerNo 得意先番号
     * @return 得意先番号の検索条件
     */
    public Specification<TEstimate> partnerNoContains(Integer partnerNo) {
        return partnerNo == null ? null : (root, query, cb) -> cb.equal(root.get("partnerNo"), partnerNo);
    }

    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    private Specification<TEstimate> goodsNameContains(String goodsName) {
        if (StringUtil.isEmpty(goodsName)) return null;
        String normalized = StringUtil.normalizeForSearch(goodsName);
        return (root, query, cb) -> {
            Join<TEstimateDetail, TEstimate> joinDetail = root.join("tEstimateDetailList");
            return cb.like(nfkc(cb, joinDetail.get("goodsName")), "%" + normalized + "%");
        };
    }

    /**
     * 商品名の複数検索条件(or接続)
     *
     * @param goodsName 商品名(半角スペースか全角スペース区切りでsplit)
     * @return 商品名の複数検索条件
     */
    public Specification<TEstimate> goodsNamesContains(String goodsName) {
        final Specification<TEstimate> noSpecification = Specification.where(null);
        List<String> goodsNames = splitQuery(goodsName);
        return goodsNames.stream()
                .map(this::goodsNameContains)
                .reduce(noSpecification, Specification::or);
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<TEstimate> goodsCodeContains(String goodsCode) {
        if (StringUtil.isEmpty(goodsCode)) return null;
        String normalized = StringUtil.normalizeForSearch(goodsCode);
        return (root, query, cb) -> {
            Join<TEstimateDetail, TEstimate> joinDetail = root.join("tEstimateDetailList");
            return cb.like(nfkc(cb, joinDetail.get("goodsCode")), "%" + normalized);
        };
    }

    /**
     * 利益率が指定の値以下であるかの検索条件
     *
     * @param profitRate 利益率
     * @return 利益率が指定の値以下であるかの検索条件
     */
    public Specification<TEstimate> profitRateContains(BigDecimal profitRate) {
        return profitRate == null ? null : (root, query, cb) -> {
            Join<TEstimateDetail, TEstimate> joinDetail = root.join("tEstimateDetailList");
            return cb.le(joinDetail.get("profitRate"), profitRate);
        };
    }

    /**
     * 見積ステータスの検索条件
     *
     * @param estimateStatus 見積ステータス
     * @return 見積ステータスの検索条件
     */
    public Specification<TEstimate> estimateStatusContains(String estimateStatus) {
        return StringUtil.isEmpty(estimateStatus) ? null : (root, query, cb) -> cb.equal(root.get("estimateStatus"), estimateStatus);
    }

    /**
     * 見積ステータスリストのin句の条件を返します
     *
     * @param estimateStatusList 見積ステータスリスト
     * @return 見積ステータスリストのin句の条件
     */
    public Specification<TEstimate> estimateStatusListContains(List<String> estimateStatusList) {
        return CollectionUtil.isEmpty(estimateStatusList) ? null : (root, query, cb) -> root.get("estimateStatus").in(estimateStatusList);
    }

    /**
     * 見積日の検索条件
     *
     * @param estimateDateFrom 見積日FROM
     * @param estimateDateTo   見積日TO
     * @return 見積日FROMの検索条件
     */
    public Specification<TEstimate> estimateDateContains(LocalDate estimateDateFrom, LocalDate estimateDateTo) {
        if (estimateDateFrom == null && estimateDateTo == null) {
            return null;
        }
        if (estimateDateTo == null) {
            // DateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("estimateDate"), estimateDateFrom);
        }
        if (estimateDateFrom == null) {
            // DateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("estimateDate"), estimateDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("estimateDate"), estimateDateFrom, estimateDateTo);
    }

    /**
     * 価格変更日の検索条件
     *
     * @param priceChangeDateFrom 価格変更日FROM
     * @param priceChangeDateTo   価格変更日TO
     * @return 価格変更日FROMの検索条件
     */
    public Specification<TEstimate> priceChangeDateRangeContains(LocalDate priceChangeDateFrom, LocalDate priceChangeDateTo) {
        if (priceChangeDateFrom == null && priceChangeDateTo == null) {
            return null;
        }
        if (priceChangeDateTo == null) {
            // DateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("priceChangeDate"), priceChangeDateFrom);
        }
        if (priceChangeDateFrom == null) {
            // DateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("priceChangeDate"), priceChangeDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("priceChangeDate"), priceChangeDateFrom, priceChangeDateTo);
    }

    /**
     * 価格変更日の検索条件（完全一致条件）
     *
     * @param priceChangeDate 価格変更日
     * @return 価格変更日の検索条件
     */
    public Specification<TEstimate> priceChangeDateContains(LocalDate priceChangeDate) {
        if (priceChangeDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("priceChangeDate"), priceChangeDate);
    }

    /**
     * 価格変更日が引数の参照日付より後である条件
     *
     * @param referenceDate 参照日付(基本的に現在日付)
     * @return 価格変更日が引数の参照日付より後である条件
     */
    public Specification<TEstimate> priceChangeDatePastContains(LocalDate referenceDate) {
        if (referenceDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("priceChangeDate"), referenceDate);
    }
}
