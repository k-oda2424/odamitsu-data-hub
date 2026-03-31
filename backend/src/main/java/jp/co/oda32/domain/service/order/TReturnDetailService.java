package jp.co.oda32.domain.service.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.embeddable.TReturnDetailPK;
import jp.co.oda32.domain.model.order.TReturnDetail;
import jp.co.oda32.domain.repository.order.TReturnDetailRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.order.TReturnDetailSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返品明細Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/28
 */
@Service
public class TReturnDetailService extends CustomService {
    private final TReturnDetailRepository tReturnDetailRepository;
    private TReturnDetailSpecification tReturnDetailSpecification = new TReturnDetailSpecification();

    @Autowired
    public TReturnDetailService(TReturnDetailRepository tReturnDetailRepository) {
        this.tReturnDetailRepository = tReturnDetailRepository;
    }

    public TReturnDetail getByPK(TReturnDetailPK pk) {
        return this.tReturnDetailRepository.getByReturnNoAndReturnDetailNo(pk.getReturnNo(), pk.getReturnDetailNo());
    }

    public List<TReturnDetail> findByOrderNo(Integer orderNo) {
        return this.tReturnDetailRepository.findByOrderNo(orderNo);
    }

    public List<TReturnDetail> findByReturnNo(Integer returnNo) {
        return this.tReturnDetailRepository.findByReturnNo(returnNo);
    }

    public List<TReturnDetail> findByDeliveryNoAndDeliveryDetailNo(int deliveryNo, int deliveryDetailNo) {
        return this.tReturnDetailRepository.findByDeliveryNoAndDeliveryDetailNo(deliveryNo, deliveryDetailNo);
    }

    public List<TReturnDetail> find(Integer shopNo, Integer companyNo, Integer orderNo, Integer orderDetailNo, String returnDetailStatus, Integer goodsNo, LocalDateTime returnDateFrom, LocalDateTime returnDateTo, String returnSlipDateFrom, String returnSlipDateTo, Flag delFlg) {
        return this.tReturnDetailRepository.findAll(Specification
                .where(this.tReturnDetailSpecification.shopNoContains(shopNo))
                .and(this.tReturnDetailSpecification.companyNoContains(companyNo))
                .and(this.tReturnDetailSpecification.orderNoContains(orderNo))
                .and(this.tReturnDetailSpecification.orderDetailNoContains(orderDetailNo))
                .and(this.tReturnDetailSpecification.returnDetailStatusContains(returnDetailStatus))
                .and(this.tReturnDetailSpecification.goodsNoContains(goodsNo))
                .and(this.tReturnDetailSpecification.returnDateTimeContains(returnDateFrom, returnDateTo))
                .and(this.tReturnDetailSpecification.returnSlipDateContains(returnSlipDateFrom, returnSlipDateTo))
                .and(this.tReturnDetailSpecification.delFlgContains(delFlg)));
    }

    /**
     * 返品明細を更新します。
     *
     * @param updateOrderDetail 更新する返品明細
     * @return 更新した返品明細Entity
     * @throws Exception システム例外
     */
    public TReturnDetail update(TReturnDetail updateOrderDetail) throws Exception {
        TReturnDetail tReturnDetail = this.getByPK(TReturnDetailPK.builder()
                .returnNo(updateOrderDetail.getReturnNo())
                .returnDetailNo(updateOrderDetail.getReturnDetailNo())
                .build());
        if (tReturnDetail == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の返品番号が見つかりません。orderNo:%d orderDetailNo:%d", updateOrderDetail.getReturnNo(), updateOrderDetail.getReturnDetailNo()));
        }
        return this.update(this.tReturnDetailRepository, updateOrderDetail);
    }

    /**
     * 返品明細を登録します。
     *
     * @param tReturnDetail 返品明細Entity
     * @return 登録した返品明細Entity
     */
    public TReturnDetail insert(TReturnDetail tReturnDetail) throws Exception {
        return this.insert(this.tReturnDetailRepository, tReturnDetail);
    }
}
