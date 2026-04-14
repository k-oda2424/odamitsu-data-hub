package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartLogistics;
import jp.co.oda32.domain.model.bcart.BCartOrder;
import jp.co.oda32.domain.model.bcart.BCartOrderProduct;
import jp.co.oda32.domain.repository.bcart.BCartLogisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

/**
 * B-cart出荷サービスクラス
 * @author k_oda
 * @since 2023/03/20
 */
@Service
@RequiredArgsConstructor
public class BCartLogisticsService {
    private final BCartLogisticsRepository bCartLogisticsRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<BCartLogistics> findByStatusIn(List<String> statuses) {
        return this.bCartLogisticsRepository.findByStatusIn(statuses);
    }

    public List<BCartLogistics> findByIdIn(List<Long> idList) {
        return this.bCartLogisticsRepository.findByIdIn(idList);
    }

    @Transactional(readOnly = true)
    public List<BCartLogistics> findByStatus(String status) {
        try {
            // ネイティブSQLクエリで基本データを取得
            List<BCartLogistics> logistics = this.bCartLogisticsRepository.findByStatusNative(status);

            // 結果が取得できた場合、各エンティティごとに関連エンティティを明示的に取得
            for (BCartLogistics logistic : logistics) {
                try {
                    // 必要な関連エンティティを手動で取得して設定
                    List<BCartOrderProduct> products = loadBCartOrderProductsForLogistics(logistic.getId());
                    logistic.setBCartOrderProductList(products);

                    // 各プロダクトに対してBCartOrderを設定
                    for (BCartOrderProduct product : products) {
                        if (product.getBCartOrder() == null && product.getOrderId() != null) {
                            // BCartOrderを手動で取得
                            loadBCartOrderForProduct(product);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Exception loading relations for logistics ID " + logistic.getId() + ": " + e.getMessage());
                    // エラーが発生した場合は空のリストを設定
                    logistic.setBCartOrderProductList(new ArrayList<>());
                }
            }

            return logistics;
        } catch (Exception e) {
            System.out.println("Error in findByStatus using native SQL: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 指定されたロジスティクスIDに関連するBCartOrderProductを取得します
     */
    private List<BCartOrderProduct> loadBCartOrderProductsForLogistics(Long logisticsId) {
        try {
            String jpql = "SELECT p FROM BCartOrderProduct p WHERE p.logisticsId = :logisticsId";
            TypedQuery<BCartOrderProduct> query = entityManager.createQuery(jpql, BCartOrderProduct.class);
            query.setParameter("logisticsId", logisticsId);
            return query.getResultList();
        } catch (Exception e) {
            System.out.println("Error loading BCartOrderProducts: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 指定されたプロダクトにBCartOrderをロードします
     */
    private void loadBCartOrderForProduct(BCartOrderProduct product) {
        try {
            BCartOrder order = entityManager.find(BCartOrder.class, product.getOrderId());
            if (order != null) {
                product.setBCartOrder(order);
            }
        } catch (Exception e) {
            System.out.println("Error loading BCartOrder: " + e.getMessage());
        }
    }

    public List<BCartLogistics> findExportableRecords() {
        return bCartLogisticsRepository.findExportableRecords();
    }

    public BCartLogistics save(BCartLogistics bCartLogistics) {
        return bCartLogisticsRepository.save(bCartLogistics);
    }

    public List<BCartLogistics> save(List<BCartLogistics> bCartLogisticsList) {
        return bCartLogisticsRepository.saveAll(bCartLogisticsList);
    }

    public BCartLogistics findById(Long id) {
        return bCartLogisticsRepository.findById(id).orElse(null);
    }

    public List<BCartLogistics> findAll() {
        return bCartLogisticsRepository.findAll();
    }

    public void updateShipmentCodes() {
        this.bCartLogisticsRepository.updateShipmentCodes();
    }

    public void deleteById(Long id) {
        bCartLogisticsRepository.deleteById(id);
    }
}
