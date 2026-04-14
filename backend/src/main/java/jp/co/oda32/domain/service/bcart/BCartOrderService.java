package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrder;
import jp.co.oda32.domain.repository.bcart.BCartOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author k_oda
 * @since 2023/03/20
 */
@Service
@RequiredArgsConstructor
public class BCartOrderService {
    private final BCartOrderRepository bCartOrderRepository;

    public BCartOrder save(BCartOrder bCartOrder) {
        return bCartOrderRepository.save(bCartOrder);
    }

    public List<BCartOrder> save(List<BCartOrder> bCartOrderList) {
        return bCartOrderRepository.saveAll(bCartOrderList);
    }

    public BCartOrder findById(Long id) {
        return bCartOrderRepository.findById(id).orElse(null);
    }

    public List<BCartOrder> findByIdIn(List<Long> idList) {
        return bCartOrderRepository.findByIdIn(idList);
    }

    /**
     * orderProductList を EAGER に取得する。
     */
    public List<BCartOrder> findWithProductsByIdIn(List<Long> idList) {
        return bCartOrderRepository.findWithProductsByIdIn(idList);
    }

    public List<BCartOrder> findByStatus(String status) {
        return this.bCartOrderRepository.findByStatus(status);
    }

    public List<BCartOrder> findAll() {
        return bCartOrderRepository.findAll();
    }

    public void deleteById(Long id) {
        bCartOrderRepository.deleteById(id);
    }
}
