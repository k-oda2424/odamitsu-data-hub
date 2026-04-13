package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartCategories;
import jp.co.oda32.domain.repository.bcart.BCartCategoriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BCartCategoriesService {

    private final BCartCategoriesRepository bCartCategoriesRepository;

    public List<BCartCategories> findAll() {
        return bCartCategoriesRepository.findAll();
    }

    public List<BCartCategories> findParentCategories() {
        return bCartCategoriesRepository.findByParentCategoryIdIsNullOrderByPriorityDesc();
    }

    public List<BCartCategories> findByParentCategoryId(Integer parentCategoryId) {
        return bCartCategoriesRepository.findByParentCategoryIdOrderByPriorityDesc(parentCategoryId);
    }

    public Optional<BCartCategories> findById(Integer id) {
        return bCartCategoriesRepository.findById(id);
    }

    public List<BCartCategories> findUnreflected() {
        return bCartCategoriesRepository.findBybCartReflectedIsFalse();
    }

    @Transactional
    public BCartCategories save(BCartCategories category) {
        return bCartCategoriesRepository.save(category);
    }

    @Transactional
    public void saveAll(List<BCartCategories> categories) {
        bCartCategoriesRepository.saveAll(categories);
    }
}
