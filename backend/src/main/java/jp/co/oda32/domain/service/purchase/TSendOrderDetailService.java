package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.SendOrderDetailStatus;
import jp.co.oda32.domain.model.embeddable.TSendOrderDetailPK;
import jp.co.oda32.domain.model.purchase.TSendOrderDetail;
import jp.co.oda32.domain.repository.purchase.TSendOrderDetailRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.purchase.TSendOrderDetailSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 発注明細Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2019/07/24
 */
@Service
public class TSendOrderDetailService extends CustomService {
    private final TSendOrderDetailRepository tSendOrderDetailRepository;
    private TSendOrderDetailSpecification tSendOrderDetailSpecification = new TSendOrderDetailSpecification();

    @Autowired
    public TSendOrderDetailService(TSendOrderDetailRepository tSendOrderDetailRepository) {
        this.tSendOrderDetailRepository = tSendOrderDetailRepository;
    }

    public List<TSendOrderDetail> findBySendOrderNo(Integer sendOrderNo) {
        return tSendOrderDetailRepository.findBySendOrderNo(sendOrderNo);
    }

    public TSendOrderDetail getByPK(TSendOrderDetailPK pk) {
        return this.tSendOrderDetailRepository.getBySendOrderNoAndSendOrderDetailNo(pk.getSendOrderNo(), pk.getSendOrderDetailNo());
    }

    public List<TSendOrderDetail> findBySendOrderDetailStatus(SendOrderDetailStatus sendOrderDetailStatus) {
        return this.tSendOrderDetailRepository.findBySendOrderDetailStatus(sendOrderDetailStatus.getCode());
    }

    public List<TSendOrderDetail> findBySendOrderDetailStatusList(List<String> sendOrderDetailStatusList) {
        return this.tSendOrderDetailRepository.findAll(Specification
                .where(this.tSendOrderDetailSpecification.sendOrderDetailStatusListContains(sendOrderDetailStatusList))
                .and(this.tSendOrderDetailSpecification.delFlgContains(Flag.NO)));
    }

    public List<TSendOrderDetail> find(Integer shopNo, Integer warehouseNo, Integer supplierNo, String sendOrderDetailStatus
            , LocalDateTime sendOrderDateFrom, LocalDateTime sendOrderDateTo, Flag delFlg) {
        return this.tSendOrderDetailRepository.findAll(Specification
                .where(this.tSendOrderDetailSpecification.shopNoContains(shopNo))
                .and(this.tSendOrderDetailSpecification.warehouseNoContains(warehouseNo))
                .and(this.tSendOrderDetailSpecification.supplierNoContains(supplierNo))
                .and(this.tSendOrderDetailSpecification.sendOrderDetailStatusContains(sendOrderDetailStatus))
                .and(this.tSendOrderDetailSpecification.sendOrderDateContains(sendOrderDateFrom, sendOrderDateTo))
                .and(this.tSendOrderDetailSpecification.delFlgContains(delFlg)));
    }


    /**
     * 発注明細を更新します。
     *
     * @param updateSendOrderDetail 更新する発注明細
     * @return 更新した発注明細Entity
     * @throws Exception システム例外
     */
    public TSendOrderDetail update(TSendOrderDetail updateSendOrderDetail) throws Exception {
        TSendOrderDetail tSendOrderDetail = this.getByPK(TSendOrderDetailPK.builder()
                .sendOrderNo(updateSendOrderDetail.getSendOrderNo())
                .sendOrderDetailNo(updateSendOrderDetail.getSendOrderDetailNo())
                .build());
        if (tSendOrderDetail == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の発注番号が見つかりません。sendOrderNo:%d sendOrderDetailNo:%d", updateSendOrderDetail.getSendOrderNo(), updateSendOrderDetail.getSendOrderDetailNo()));
        }
        return this.update(this.tSendOrderDetailRepository, updateSendOrderDetail);
    }

    public List<TSendOrderDetail> update(List<TSendOrderDetail> sendOrderDetailList) throws Exception {
        List<TSendOrderDetail> updatedSendOrderDetailList = new ArrayList<>();
        for (TSendOrderDetail sendOrderDetail : sendOrderDetailList) {
            updatedSendOrderDetailList.add(this.update(sendOrderDetail));
        }
        return updatedSendOrderDetailList;
    }

    /**
     * 発注明細を登録します。
     *
     * @param tSendOrderDetail 発注明細Entity
     * @return 登録した発注明細Entity
     */
    public TSendOrderDetail insert(TSendOrderDetail tSendOrderDetail) throws Exception {
        return this.insert(this.tSendOrderDetailRepository, tSendOrderDetail);
    }

    public List<TSendOrderDetail> insert(List<TSendOrderDetail> tSendOrderDetailList) throws Exception {
        List<TSendOrderDetail> insertList = new ArrayList<>();
        for (TSendOrderDetail tSendOrderDetail : tSendOrderDetailList) {
            TSendOrderDetail detail = this.insert(tSendOrderDetail);
            insertList.add(detail);
        }
        return insertList;
    }
}
