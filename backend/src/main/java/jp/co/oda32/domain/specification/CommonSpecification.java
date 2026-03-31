package jp.co.oda32.domain.specification;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommonSpecification<T extends IEntity> {

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
}
