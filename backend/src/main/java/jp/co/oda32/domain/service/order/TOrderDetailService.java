package jp.co.oda32.domain.service.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.OrderDetailStatus;
import jp.co.oda32.domain.model.embeddable.TOrderDetailPK;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.repository.order.TOrderDetailRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.order.TOrderDetailSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 注文明細Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/28
 */
@Service
public class TOrderDetailService extends CustomService {
    private final TOrderDetailRepository tOrderDetailRepository;
    private TOrderDetailSpecification tOrderDetailSpecification = new TOrderDetailSpecification();

    @Autowired
    public TOrderDetailService(TOrderDetailRepository tOrderDetailRepository) {
        this.tOrderDetailRepository = tOrderDetailRepository;
    }

    public List<TOrderDetail> findByOrderNo(Integer orderNo) {
        return tOrderDetailRepository.findByOrderNo(orderNo);
    }

    /**
     * 出荷番号リストに関連する注文明細を検索します
     *
     * @param deliveryNos 出荷番号リスト
     * @return 注文明細リスト
     */
    public List<TOrderDetail> findByDeliveryNos(List<Integer> deliveryNos) {
        return this.tOrderDetailRepository.findByDeliveryNos(deliveryNos);
    }

    public TOrderDetail getByPK(TOrderDetailPK pk) {
        return this.tOrderDetailRepository.getByOrderNoAndOrderDetailNo(pk.getOrderNo(), pk.getOrderDetailNo());
    }

    public List<TOrderDetail> findByOrderDetailStatus(OrderDetailStatus orderDetailStatus) {
        return this.tOrderDetailRepository.findByOrderDetailStatus(orderDetailStatus.getCode());
    }

    public List<TOrderDetail> findByOrderNoList(List<Integer> orderNoList) {
        return this.tOrderDetailRepository.findAll(Specification
                .where(this.tOrderDetailSpecification.orderNoListContains(orderNoList)));
    }

    public List<TOrderDetail> findByOrderDetailStatusList(String... orderDetailStatuses) {
        return this.tOrderDetailRepository.findAll(Specification
                .where(this.tOrderDetailSpecification.orderDetailStatusListContains(orderDetailStatuses)));

    }

    public List<TOrderDetail> findByDeliveryNo(Integer deliveryNo) {
        return this.tOrderDetailRepository.findByDeliveryNo(deliveryNo);
    }

    public List<TOrderDetail> find(Integer shopNo, Integer companyNo, Integer orderNo, Integer orderDetailNo, String slipNo
            , String[] orderDetailStatus, Integer goodsNo, String goodsCode, String goodsName, LocalDateTime orderDateFrom, LocalDateTime orderDateTo
            , LocalDate slipDateFrom, LocalDate slipDateTo, Flag delFlg) {
        return this.tOrderDetailRepository.findAll(Specification
                .where(this.tOrderDetailSpecification.shopNoContains(shopNo))
                .and(this.tOrderDetailSpecification.companyNoContains(companyNo))
                .and(this.tOrderDetailSpecification.orderNoContains(orderNo))
                .and(this.tOrderDetailSpecification.orderDetailNoContains(orderDetailNo))
                .and(this.tOrderDetailSpecification.slipNoContains(slipNo))
                .and(this.tOrderDetailSpecification.orderDetailStatusListContains(orderDetailStatus))
                .and(this.tOrderDetailSpecification.goodsNoContains(goodsNo))
                .and(this.tOrderDetailSpecification.goodsCodeContains(goodsCode))
                .and(this.tOrderDetailSpecification.goodsNameContains(goodsName))
                .and(this.tOrderDetailSpecification.orderDateTimeContains(orderDateFrom, orderDateTo))
                .and(this.tOrderDetailSpecification.slipDateContains(slipDateFrom, slipDateTo))
                .and(this.tOrderDetailSpecification.delFlgContains(delFlg)));
    }

    /**
     * 受注一覧画面用の検索（partnerNoを含む）
     */
    public List<TOrderDetail> searchForList(Integer shopNo, Integer companyNo, Integer partnerNo, String slipNo,
                                            String goodsName, String goodsCode, String[] orderDetailStatus,
                                            LocalDateTime orderDateTimeFrom, LocalDateTime orderDateTimeTo,
                                            LocalDate slipDateFrom, LocalDate slipDateTo, Flag delFlg) {
        return this.tOrderDetailRepository.findAll(Specification
                .where(this.tOrderDetailSpecification.shopNoContains(shopNo))
                .and(this.tOrderDetailSpecification.companyNoContains(companyNo))
                .and(this.tOrderDetailSpecification.partnerNoContains(partnerNo))
                .and(this.tOrderDetailSpecification.slipNoContains(slipNo))
                .and(this.tOrderDetailSpecification.orderDetailStatusListContains(orderDetailStatus))
                .and(this.tOrderDetailSpecification.goodsCodeContains(goodsCode))
                .and(this.tOrderDetailSpecification.goodsNameContains(goodsName))
                .and(this.tOrderDetailSpecification.orderDateTimeContains(orderDateTimeFrom, orderDateTimeTo))
                .and(this.tOrderDetailSpecification.slipDateContains(slipDateFrom, slipDateTo))
                .and(this.tOrderDetailSpecification.delFlgContains(delFlg)));
    }

    /**
     * 伝票日付の範囲で検索します
     *
     * @param slipDateFrom 伝票日付from
     * @param slipDateTo   伝票日付to
     * @param delFlg       削除フラグ
     * @return 伝票日付の範囲の検索結果
     */
    public List<TOrderDetail> find(LocalDate slipDateFrom, LocalDate slipDateTo, Flag delFlg) {
        return this.tOrderDetailRepository.findAll(Specification
                .where(this.tOrderDetailSpecification.slipDateContains(slipDateFrom, slipDateTo))
                .and(this.tOrderDetailSpecification.delFlgContains(delFlg)));
    }

    /**
     * 指定した得意先リストと伝票日付範囲で検索します
     *
     * @param partnerNos   得意先番号リスト
     * @param slipDateFrom 伝票日付from
     * @param slipDateTo   伝票日付to
     * @param delFlg       削除フラグ
     * @return 検索結果
     */
    public List<TOrderDetail> findByPartnerNosAndDateRange(List<Integer> partnerNos, LocalDate slipDateFrom, LocalDate slipDateTo, Flag delFlg) {
        return this.tOrderDetailRepository.findAll(Specification
                .where(this.tOrderDetailSpecification.partnerNoListContains(partnerNos))
                .and(this.tOrderDetailSpecification.slipDateContains(slipDateFrom, slipDateTo))
                .and(this.tOrderDetailSpecification.delFlgContains(delFlg)));
    }

    /**
     * 注文明細を更新します。
     *
     * @param updateOrderDetail 更新する注文明細
     * @return 更新した注文明細Entity
     * @throws Exception システム例外
     */
    public TOrderDetail update(TOrderDetail updateOrderDetail) throws Exception {
        TOrderDetail tOrderDetail = this.getByPK(TOrderDetailPK.builder()
                .orderNo(updateOrderDetail.getOrderNo())
                .orderDetailNo(updateOrderDetail.getOrderDetailNo())
                .build());
        if (tOrderDetail == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の注文番号が見つかりません。orderNo:%d orderDetailNo:%d", updateOrderDetail.getOrderNo(), updateOrderDetail.getOrderDetailNo()));
        }
        return this.update(this.tOrderDetailRepository, updateOrderDetail);
    }

    public List<TOrderDetail> update(List<TOrderDetail> orderDetailList) throws Exception {
        List<TOrderDetail> updatedOrderDetailList = new ArrayList<>();
        for (TOrderDetail orderDetail : orderDetailList) {
            updatedOrderDetailList.add(this.update(orderDetail));
        }
        return updatedOrderDetailList;
    }

    /**
     * 注文明細を登録します。
     *
     * @param tOrderDetail 注文明細Entity
     * @return 登録した注文明細Entity
     */
    public TOrderDetail insert(TOrderDetail tOrderDetail) throws Exception {
        return this.insert(this.tOrderDetailRepository, tOrderDetail);
    }

    /**
     * 注文明細を物理的に削除します。
     *
     * @param deleteEntity 削除対象Entity
     * @throws Exception 例外発生時
     */
    public void deletePermanently(TOrderDetail deleteEntity) throws Exception {
        this.deletePermanently(this.tOrderDetailRepository, deleteEntity);
    }
}