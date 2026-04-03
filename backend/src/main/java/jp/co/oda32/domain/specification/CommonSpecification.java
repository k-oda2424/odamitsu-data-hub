package jp.co.oda32.domain.specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommonSpecification<T> {

    public Specification<T> delFlgContains(Flag flag) {
        return flag == null ? null : (root, query, cb) -> cb.equal(root.get("delFlg"), flag.getValue());
    }

    protected List<String> splitQuery(String query) {
        if (StringUtil.isEmpty(query)) {
            return new ArrayList<>();
        }
        final String space = " ";
        final String spacesPattern = "[\\s\u3000]+";
        final String monoSpaceQuery = query.replaceAll(spacesPattern, space);
        final String trimmedMonoSpaceQuery = monoSpaceQuery.trim();
        return Arrays.asList(trimmedMonoSpaceQuery.split("\\s"));
    }

    /**
     * 全角・半角を統一（NFKC正規化）してLIKE検索するSpecificationを返します。
     * パターン: %value%（部分一致）
     */
    protected Specification<T> likeNormalized(String fieldName, String searchValue) {
        if (StringUtil.isEmpty(searchValue)) return null;
        String normalized = StringUtil.normalizeForSearch(searchValue);
        return (root, query, cb) -> cb.like(
                nfkc(cb, root.get(fieldName)),
                "%" + normalized + "%"
        );
    }

    /**
     * NFKC正規化して後方一致LIKE検索（%value）
     */
    protected Specification<T> likeSuffixNormalized(String fieldName, String searchValue) {
        if (StringUtil.isEmpty(searchValue)) return null;
        String normalized = StringUtil.normalizeForSearch(searchValue);
        return (root, query, cb) -> cb.like(
                nfkc(cb, root.get(fieldName)),
                "%" + normalized
        );
    }

    /**
     * NFKC正規化して前方一致LIKE検索（value%）
     */
    protected Specification<T> likePrefixNormalized(String fieldName, String searchValue) {
        if (StringUtil.isEmpty(searchValue)) return null;
        String normalized = StringUtil.normalizeForSearch(searchValue);
        return (root, query, cb) -> cb.like(
                nfkc(cb, root.get(fieldName)),
                normalized + "%"
        );
    }

    /**
     * ネストされたパスに対するNFKC正規化LIKE（部分一致）
     * 例: root.get("mGoods").get("goodsName")
     */
    protected Specification<T> likeNormalized(String parent, String child, String searchValue) {
        if (StringUtil.isEmpty(searchValue)) return null;
        String normalized = StringUtil.normalizeForSearch(searchValue);
        return (root, query, cb) -> cb.like(
                nfkc(cb, root.get(parent).get(child)),
                "%" + normalized + "%"
        );
    }

    /**
     * ネストされたパスに対するNFKC正規化LIKE — 後方一致（%value）
     */
    protected Specification<T> likeSuffixNormalized(String parent, String child, String searchValue) {
        if (StringUtil.isEmpty(searchValue)) return null;
        String normalized = StringUtil.normalizeForSearch(searchValue);
        return (root, query, cb) -> cb.like(
                nfkc(cb, root.get(parent).get(child)),
                "%" + normalized
        );
    }

    /**
     * PostgreSQL nfkc() 関数を呼び出すExpression
     */
    protected Expression<String> nfkc(jakarta.persistence.criteria.CriteriaBuilder cb, Path<String> path) {
        return cb.function("nfkc", String.class, path);
    }
}
