package jp.co.oda32.domain.service.bcart;

/**
 * @author k_oda
 * @since 2023/04/10
 */

import jp.co.oda32.domain.model.bcart.DeliveryMapping;
import jp.co.oda32.domain.repository.bcart.DeliveryMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryMappingService {

    /** allocateSmileDeliveryCode の衝突リトライ上限。 */
    private static final int ALLOCATE_RETRY_MAX = 10;

    private final DeliveryMappingRepository deliveryMappingRepository;

    @Transactional
    public DeliveryMapping save(DeliveryMapping deliveryMapping) {
        return deliveryMappingRepository.save(deliveryMapping);
    }

    @Transactional
    public List<DeliveryMapping> saveAll(List<DeliveryMapping> deliveryMappings) {
        return deliveryMappingRepository.saveAll(deliveryMappings);
    }

    public List<DeliveryMapping> findBybCartCustomerId(Long bCartCustomerId) {
        return this.deliveryMappingRepository.findBybCartCustomerId(bCartCustomerId);
    }

    public List<DeliveryMapping> findBySmileCsvOutputtedFalse() {
        return this.deliveryMappingRepository.findBySmileCsvOutputtedFalse();
    }

    /** 指定顧客配下の smile_delivery_code の最大値（数値化、空なら 0） */
    public Integer findMaxSmileDeliveryCodeNumber(Long bCartCustomerId) {
        Integer v = this.deliveryMappingRepository.findMaxSmileDeliveryCodeNumber(bCartCustomerId);
        return v != null ? v : 0;
    }

    /**
     * 新規 DeliveryMapping に連番 smile_delivery_code を割り当てて保存する。
     *
     * <p>MAX(code)+1 の読み取り→書き込みは race condition を持つため、
     * {@link DataIntegrityViolationException} を捕捉して MAX を引き直し、
     * 次の空き番号で再試行する。最終的な一意性保証には
     * {@code (b_cart_customer_id, smile_delivery_code)} への UNIQUE 制約が必要
     * （現時点で未設定の場合は最悪時に重複が入り得るため、スキーマ追加を推奨）。
     *
     * <p>各リトライは独立した REQUIRES_NEW tx とすることで、衝突時に tx 全体が
     * rollback-only マークされても次のリトライに影響しないようにする。
     *
     * @return 割り当てた smile_delivery_code（6桁ゼロ埋め）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String allocateSmileDeliveryCodeAndSave(DeliveryMapping mapping, Long bCartCustomerId) {
        DataIntegrityViolationException lastError = null;
        for (int attempt = 0; attempt < ALLOCATE_RETRY_MAX; attempt++) {
            int nextCode = findMaxSmileDeliveryCodeNumber(bCartCustomerId) + 1 + attempt;
            String code = String.format("%06d", nextCode);
            mapping.setSmileDeliveryCode(code);
            try {
                deliveryMappingRepository.saveAndFlush(mapping);
                return code;
            } catch (DataIntegrityViolationException e) {
                lastError = e;
                log.warn("smile_delivery_code={} が衝突。再試行: attempt={} customerId={}",
                        code, attempt + 1, bCartCustomerId);
            }
        }
        throw new IllegalStateException(
                "smile_delivery_code の採番に " + ALLOCATE_RETRY_MAX
                        + " 回失敗しました: customerId=" + bCartCustomerId, lastError);
    }
}
