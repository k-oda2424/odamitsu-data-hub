package jp.co.oda32.domain.service.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.OrderStatus;
import jp.co.oda32.domain.model.order.TOrder;
import jp.co.oda32.domain.repository.order.TOrderRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.order.TOrderSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 注文Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class TOrderService extends CustomService {
    private final TOrderRepository tOrderRepository;
    private TOrderSpecification tOrderSpecification = new TOrderSpecification();

    @Autowired
    public TOrderService(TOrderRepository tOrderRepository) {
        this.tOrderRepository = tOrderRepository;
    }

    public List<TOrder> findAll() {
        return tOrderRepository.findAll();
    }

    public TOrder getByPK(Integer orderNo) {
        return this.tOrderRepository.findById(orderNo).orElseThrow();
    }

    /**
     * 注文番号リストを元に注文情報を取得します
     *
     * @param orderNos 注文番号リスト
     * @return 注文リスト
     */
    public List<TOrder> findByOrderNoIn(List<Integer> orderNos) {
        return this.tOrderRepository.findByOrderNoIn(orderNos);
    }

    public List<TOrder> find(Integer shopNo, Integer companyNo, String companyName, String orderStatus, LocalDateTime orderDateFrom, LocalDateTime orderDateTo, String slipDate, String route, Flag delFlg) {
        return this.tOrderRepository.findAll(Specification
                .where(this.tOrderSpecification.shopNoContains(shopNo))
                .and(this.tOrderSpecification.companyNoContains(companyNo))
                .and(this.tOrderSpecification.companyNameContains(companyName))
                .and(this.tOrderSpecification.orderStatusContains(orderStatus))
                .and(this.tOrderSpecification.orderDateTimeContains(orderDateFrom, orderDateTo))
                .and(this.tOrderSpecification.slipDateContains(slipDate))
                .and(this.tOrderSpecification.orderRouteContains(route))
                .and(this.tOrderSpecification.delFlgContains(delFlg)));
    }

    public List<TOrder> findByOrderNoList(List<Integer> orderNoList) {
        return this.tOrderRepository.findAll(Specification
                .where(this.tOrderSpecification.orderNoListContains(orderNoList)));
    }

    /**
     * shop_noとprocessing_serial_numberのユニークキーで検索します。
     *
     * @param shopNo                 ショップ番号
     * @param processingSerialNumber smile処理連番
     * @return 注文情報
     */
    public TOrder getByUniqKey(int shopNo, long processingSerialNumber) {
        return this.tOrderRepository.getByShopNoAndProcessingSerialNumber(shopNo, processingSerialNumber);
    }

    /**
     * 注文を更新します。
     *
     * @param updateOrder 注文Entity
     * @return 更新した注文Entity
     * @throws Exception システム例外
     */
    public TOrder update(TOrder updateOrder) throws Exception {
        TOrder tOrder = this.getByPK(updateOrder.getOrderNo());
        if (tOrder == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の注文番号が見つかりません。orderNo:%d", updateOrder.getOrderNo()));
        }
        return this.update(this.tOrderRepository, updateOrder);
    }

    public int updateOrderStatusByOrderNoList(List<Integer> orderNoList, OrderStatus orderStatus) {
        return this.tOrderRepository.updateOrderStatusByOrderNoList(orderStatus.getValue(), orderNoList);
    }

    /**
     * 注文を登録します。
     *
     * @param tOrder 注文Entity
     * @return 登録した注文Entity
     */
    public TOrder insert(TOrder tOrder) throws Exception {
        return this.insert(this.tOrderRepository, tOrder);
    }

    /**
     * 注文を物理的に削除します。
     *
     * @param deleteEntity 削除対象Entity
     * @throws Exception 例外発生時
     */
    public void deletePermanently(TOrder deleteEntity) throws Exception {
        this.deletePermanently(this.tOrderRepository, deleteEntity);
    }
}