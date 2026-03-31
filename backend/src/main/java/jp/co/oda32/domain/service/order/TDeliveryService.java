package jp.co.oda32.domain.service.order;

import jp.co.oda32.constant.DeliveryStatus;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.order.TDelivery;
import jp.co.oda32.domain.repository.order.TDeliveryRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.order.TDeliverySpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 出荷Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class TDeliveryService extends CustomService {
    private final TDeliveryRepository tDeliveryRepository;
    private TDeliverySpecification tDeliverySpecification = new TDeliverySpecification();

    @Autowired
    public TDeliveryService(TDeliveryRepository tDeliveryRepository) {
        this.tDeliveryRepository = tDeliveryRepository;
    }

    /**
     * ユニークキーで検索します。
     *
     * @param shopNo      ショップ番号
     * @param partnerCode 得意先コード
     * @param slipNo      伝票番号
     * @return 出荷Entity
     */
    public TDelivery getByShopNoAndPartnerCodeAndSlipNo(Integer shopNo, String partnerCode, String slipNo) {
        return tDeliveryRepository.getByShopNoAndPartnerCodeAndSlipNo(shopNo, partnerCode, slipNo);
    }

    public TDelivery getByPK(Integer deliveryNo) {
        return this.tDeliveryRepository.findById(deliveryNo).orElseThrow();
    }

    /**
     * shop_noとprocessing_serial_numberのユニークキーで検索します。
     *
     * @param shopNo                 ショップ番号
     * @param processingSerialNumber smile処理連番
     * @return 注文情報
     */
    public TDelivery getByUniqKey(int shopNo, long processingSerialNumber) {
        return this.tDeliveryRepository.getByShopNoAndProcessingSerialNumber(shopNo, processingSerialNumber);
    }
    public List<TDelivery> find(Integer shopNo, Integer companyNo, String companyName, String orderStatus, LocalDate orderDateFrom, LocalDate orderDateTo, String slipDate, String route, Flag delFlg) {
        return this.tDeliveryRepository.findAll(Specification
                .where(this.tDeliverySpecification.shopNoContains(shopNo))
                .and(this.tDeliverySpecification.companyNoContains(companyNo))
                .and(this.tDeliverySpecification.companyNameContains(companyName))
                .and(this.tDeliverySpecification.orderStatusContains(orderStatus))
                .and(this.tDeliverySpecification.deliveryDateContains(orderDateFrom, orderDateTo))
                .and(this.tDeliverySpecification.slipDateContains(slipDate))
                .and(this.tDeliverySpecification.delFlgContains(delFlg)));
    }

    /**
     * 出荷番号リストで出荷を検索します。
     *
     * @param deliveryNoList 出荷番号リスト
     * @return 出荷のリスト
     */
    public List<TDelivery> findByDeliveryNoList(List<Integer> deliveryNoList) {
        return this.tDeliveryRepository.findAll(Specification
                .where(this.tDeliverySpecification.deliveryNoListContains(deliveryNoList))
                .and(this.tDeliverySpecification.delFlgContains(Flag.NO)));
    }

    /**
     * 出荷ステータスを更新します
     *
     * @param deliveryNoList 更新する出荷番号リスト
     * @param deliveryStatus 　出荷明細ステータス
     * @return 更新件数
     */
    public int updateDeliveryStatusByDeliveryNoList(List<Integer> deliveryNoList, DeliveryStatus deliveryStatus) {
        return this.tDeliveryRepository.updateDeliveryStatusByDeliveryNoList(deliveryStatus.getValue(), deliveryNoList);
    }

    /**
     * 出荷日がnullの場合、出荷日を出荷予定日で更新する
     *
     * @param deliveryNoList 出荷日を入れたい出荷番号リスト
     * @return 更新件数
     */
    public int updateDeliveryDateByDeliveryNoList(List<Integer> deliveryNoList) {
        return this.tDeliveryRepository.updateDeliveryDateByDeliveryNoList(deliveryNoList);
    }

    /**
     * 出荷を更新します。
     *
     * @param tDelivery 出荷Entity
     * @return 更新した出荷Entity
     * @throws Exception システム例外
     */
    public TDelivery update(TDelivery tDelivery) throws Exception {
        return this.update(this.tDeliveryRepository, tDelivery);
    }

    /**
     * 出荷を登録します。
     *
     * @param tDelivery 出荷Entity
     * @return 登録した出荷Entity
     */
    public TDelivery insert(TDelivery tDelivery) throws Exception {
        return this.insert(this.tDeliveryRepository, tDelivery);
    }

    /**
     * 出荷を物理的に削除します。
     *
     * @param deleteEntity 削除対象Entity
     * @throws Exception 例外発生時
     */
    public void deletePermanently(TDelivery deleteEntity) throws Exception {
        this.deletePermanently(this.tDeliveryRepository, deleteEntity);
    }
}