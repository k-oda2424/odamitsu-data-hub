package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.domain.model.purchase.TSendOrder;
import jp.co.oda32.domain.repository.purchase.TSendOrderRepository;
import jp.co.oda32.domain.service.CustomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 発注Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2019/07/24
 */
@Service
public class TSendOrderService extends CustomService {
    private final TSendOrderRepository tSendOrderRepository;

    @Autowired
    public TSendOrderService(TSendOrderRepository tSendOrderRepository) {
        this.tSendOrderRepository = tSendOrderRepository;
    }

    public TSendOrder getByPK(Integer sendOrderNo) {
        return this.tSendOrderRepository.findById(sendOrderNo).orElseThrow();
    }

    /**
     * 発注を更新します。
     *
     * @param updateOrder 発注Entity
     * @return 更新した発注Entity
     * @throws Exception システム例外
     */
    public TSendOrder update(TSendOrder updateOrder) throws Exception {
        TSendOrder tOrder = this.getByPK(updateOrder.getSendOrderNo());
        if (tOrder == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の発注番号が見つかりません。sendOrderNo:%d", updateOrder.getSendOrderNo()));
        }
        return this.update(this.tSendOrderRepository, updateOrder);
    }

    /**
     * 発注を登録します。
     *
     * @param tOrder 発注Entity
     * @return 登録した発注Entity
     */
    public TSendOrder insert(TSendOrder tOrder) throws Exception {
        return this.insert(this.tSendOrderRepository, tOrder);
    }
}
