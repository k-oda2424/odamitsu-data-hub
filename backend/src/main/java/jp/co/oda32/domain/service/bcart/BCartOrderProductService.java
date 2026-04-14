package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrderProduct;
import jp.co.oda32.domain.repository.bcart.BCartOrderProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author k_oda
 * @since 2023/03/20
 */
@Service
@RequiredArgsConstructor
public class BCartOrderProductService {
    private final BCartOrderProductRepository bCartOrderProductRepository;

    public BCartOrderProduct save(BCartOrderProduct bCartOrder) {
        return bCartOrderProductRepository.save(bCartOrder);
    }

    public List<BCartOrderProduct> save(List<BCartOrderProduct> bCartOrderProductList) {
        List<BCartOrderProduct> savedList = new ArrayList<>();
        for (BCartOrderProduct bCartOrderProduct : bCartOrderProductList) {
            savedList.add(this.save(bCartOrderProduct));
        }
        return savedList;
    }

    public BCartOrderProduct findById(Long id) {
        return bCartOrderProductRepository.findById(id).orElse(null);
    }

    public List<BCartOrderProduct> findAll() {
        return bCartOrderProductRepository.findAll();
    }

    public void deleteById(Long id) {
        bCartOrderProductRepository.deleteById(id);
    }

    /**
     * 指定したロジスティクスIDリストに関連するBCartOrderProductを一括取得する
     * JOIN FETCHにより関連するBCartOrderも一緒に取得される
     *
     * @param logisticsIds ロジスティクスIDのリスト
     * @return 関連するBCartOrderProductのリスト
     */
    public List<BCartOrderProduct> findByLogisticsIdIn(List<Long> logisticsIds) {
        return bCartOrderProductRepository.findByLogisticsIdIn(logisticsIds);
    }
}
