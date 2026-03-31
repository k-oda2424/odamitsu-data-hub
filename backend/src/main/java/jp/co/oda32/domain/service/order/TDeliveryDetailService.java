package jp.co.oda32.domain.service.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.embeddable.TDeliveryDetailPK;
import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.domain.repository.order.TDeliveryDetailRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.order.TDeliveryDetailSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 出荷明細Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/28
 */
@Service
public class TDeliveryDetailService extends CustomService {
    private final TDeliveryDetailRepository tDeliveryDetailRepository;
    private TDeliveryDetailSpecification tDeliveryDetailSpecification = new TDeliveryDetailSpecification();

    @Autowired
    public TDeliveryDetailService(TDeliveryDetailRepository tDeliveryDetailRepository) {
        this.tDeliveryDetailRepository = tDeliveryDetailRepository;
    }

    public List<TDeliveryDetail> findByDeliveryNo(int deliveryNo) {
        return tDeliveryDetailRepository.findByDeliveryNo(deliveryNo);
    }

    public List<TDeliveryDetail> findDeletTDeliveryList() {
        return this.tDeliveryDetailRepository.findDeletTDeliveryList();
    }

    /**
     * 出荷番号と注文明細番号に関連する出荷明細を検索します
     *
     * @param deliveryNo    出荷番号
     * @param orderDetailNo 注文明細番号
     * @return 出荷明細リスト
     */
    public List<TDeliveryDetail> findByDeliveryNoAndOrderDetailNo(Integer deliveryNo, Integer orderDetailNo) {
        return this.tDeliveryDetailRepository.findByDeliveryNoAndOrderDetailNo(deliveryNo, orderDetailNo);
    }

    public TDeliveryDetail getByPK(TDeliveryDetailPK pk) {
        return this.tDeliveryDetailRepository.getByDeliveryNoAndDeliveryDetailNo(pk.getDeliveryNo(), pk.getDeliveryDetailNo());
    }

    public List<TDeliveryDetail> find(Integer shopNo, Integer companyNo, Integer deliveryNo, Integer deliveryDetailNo, String deliveryDetailStatus, Integer goodsNo
            , LocalDate deliveryDateFrom, LocalDate deliveryDateTo, LocalDate slipDateFrom, LocalDate slipDateTo, Flag delFlg) {
        return this.tDeliveryDetailRepository.findAll(Specification
                .where(this.tDeliveryDetailSpecification.shopNoContains(shopNo))
                .and(this.tDeliveryDetailSpecification.companyNoContains(companyNo))
                .and(this.tDeliveryDetailSpecification.deliveryNoContains(deliveryNo))
                .and(this.tDeliveryDetailSpecification.deliveryDetailNoContains(deliveryDetailNo))
                .and(this.tDeliveryDetailSpecification.deliveryDetailStatusContains(deliveryDetailStatus))
                .and(this.tDeliveryDetailSpecification.goodsNoContains(goodsNo))
                .and(this.tDeliveryDetailSpecification.deliveryDateContains(deliveryDateFrom, deliveryDateTo))
                .and(this.tDeliveryDetailSpecification.slipDateContains(slipDateFrom, slipDateTo))
                .and(this.tDeliveryDetailSpecification.delFlgContains(delFlg)));
    }

    public List<TDeliveryDetail> find(List<Integer> companyNoList, List<Integer> goodsNoList, String deliveryDetailStatus, LocalDate deliveryDateFrom, LocalDate deliveryDateTo, LocalDate slipDateFrom, LocalDate slipDateTo, String matApiFlg, Flag delFlg) {
        return this.tDeliveryDetailRepository.findAll(Specification
                .where(this.tDeliveryDetailSpecification.companyNoListContains(companyNoList))
                .and(this.tDeliveryDetailSpecification.goodsNoListContains(goodsNoList))
                .and(this.tDeliveryDetailSpecification.deliveryDetailStatusContains(deliveryDetailStatus))
                .and(this.tDeliveryDetailSpecification.deliveryDateContains(deliveryDateFrom, deliveryDateTo))
                .and(this.tDeliveryDetailSpecification.slipDateContains(slipDateFrom, slipDateTo))
                .and(this.tDeliveryDetailSpecification.matApiFlgContains(matApiFlg))
                .and(this.tDeliveryDetailSpecification.delFlgContains(delFlg)));
    }

    public List<TDeliveryDetail> findForUpdateDeliveryDetailStatus(String deliveryDetailStatus, String orderDetailStatus, Flag delFlg) {
        return this.tDeliveryDetailRepository.findAll(Specification
                .where(this.tDeliveryDetailSpecification.deliveryDetailStatusContains(deliveryDetailStatus))
                .and(this.tDeliveryDetailSpecification.orderDetailStatusContains(orderDetailStatus))
                .and(this.tDeliveryDetailSpecification.delFlgContains(delFlg)));
    }

    public List<TDeliveryDetail> findByDeliveryNoList(List<Integer> deliveryNoList) {
        return this.tDeliveryDetailRepository.findAll(Specification
                .where(this.tDeliveryDetailSpecification.deliveryNoListContains(deliveryNoList)));
    }

    public List<TDeliveryDetail> findBySlipNoList(Integer shopNo, List<String> slipNoList) {
        return this.tDeliveryDetailRepository.findAll(Specification
                .where(this.tDeliveryDetailSpecification.shopNoContains(shopNo))
                .and(this.tDeliveryDetailSpecification.slipNoListContains(slipNoList))
                .and(this.tDeliveryDetailSpecification.delFlgContains(Flag.NO)));
    }

    /**
     * 出荷明細を更新します。
     *
     * @param updateDeliveryDetail 更新する出荷明細
     * @return 更新した出荷明細Entity
     * @throws Exception システム例外
     */
    public TDeliveryDetail update(TDeliveryDetail updateDeliveryDetail) throws Exception {
        TDeliveryDetail tDeliveryDetail = this.getByPK(TDeliveryDetailPK.builder()
                .deliveryNo(updateDeliveryDetail.getDeliveryNo())
                .deliveryDetailNo(updateDeliveryDetail.getDeliveryDetailNo())
                .build());
        if (tDeliveryDetail == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の出荷番号が見つかりません。deliveryNo:%d deliveryDetailNo:%d", updateDeliveryDetail.getDeliveryNo(), updateDeliveryDetail.getDeliveryDetailNo()));
        }
        return this.update(this.tDeliveryDetailRepository, updateDeliveryDetail);
    }

    public List<TDeliveryDetail> update(List<TDeliveryDetail> deliveryDetailList) throws Exception {
        List<TDeliveryDetail> updatedDeliveryDetailList = new ArrayList<>();
        for (TDeliveryDetail deliveryDetail : deliveryDetailList) {
            updatedDeliveryDetailList.add(this.update(deliveryDetail));
        }
        return updatedDeliveryDetailList;
    }

    /**
     * 出荷明細を登録します。
     *
     * @param tDeliveryDetail 出荷明細Entity
     * @return 登録した出荷明細Entity
     */
    public TDeliveryDetail insert(TDeliveryDetail tDeliveryDetail) throws Exception {
        return this.insert(this.tDeliveryDetailRepository, tDeliveryDetail);
    }

    /**
     * 出荷明細を物理的に削除します。
     *
     * @param deleteEntity 削除対象Entity
     * @throws Exception 例外発生時
     */
    public void deletePermanently(TDeliveryDetail deleteEntity) throws Exception {
        this.deletePermanently(this.tDeliveryDetailRepository, deleteEntity);
    }
}