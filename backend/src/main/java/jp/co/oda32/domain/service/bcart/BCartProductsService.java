package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartProducts;
import jp.co.oda32.domain.repository.bcart.BCartProductsRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BCartProductsService {

    private final BCartProductsRepository bCartProductsRepository;

    @Transactional
    public BCartProducts save(BCartProducts bCartProducts) {
        return bCartProductsRepository.save(bCartProducts);
    }

    public Optional<BCartProducts> findById(Integer id) {
        return bCartProductsRepository.findById(id);
    }

    public List<BCartProducts> findAll() {
        return bCartProductsRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<BCartProducts> search(String name, Integer categoryId, String flag) {
        Specification<BCartProducts> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isBlank()) {
                String escaped = name.toLowerCase()
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + escaped + "%"));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (flag != null && !flag.isBlank()) {
                predicates.add(cb.equal(root.get("flag"), flag));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<BCartProducts> results = bCartProductsRepository.findAll(spec);
        // LAZYコレクションをセッション内で初期化（LazyInitializationException回避）
        results.forEach(p -> Hibernate.initialize(p.getBCartProductSets()));
        return results;
    }

    @Transactional(readOnly = true)
    public Optional<BCartProducts> findByIdWithSets(Integer id) {
        Optional<BCartProducts> opt = bCartProductsRepository.findById(id);
        opt.ifPresent(p -> Hibernate.initialize(p.getBCartProductSets()));
        return opt;
    }
}
