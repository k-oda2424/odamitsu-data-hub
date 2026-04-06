package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartCategories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BCartCategoriesRepository extends JpaRepository<BCartCategories, Integer> {
    List<BCartCategories> findByParentCategoryIdIsNullOrderByPriorityDesc();

    List<BCartCategories> findByParentCategoryIdOrderByPriorityDesc(Integer parentCategoryId);

    List<BCartCategories> findBybCartReflectedIsFalse();

    List<BCartCategories> findByFlagOrderByPriorityDesc(Integer flag);
}
