package jp.co.oda32.domain.service.order;

import jp.co.oda32.domain.model.order.MDeliveryDestination;
import jp.co.oda32.domain.repository.master.MDeliveryDestinationRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.util.CollectionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 届け先Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/12/15
 */
@Service
public class MDeliveryDestinationService extends CustomService {
    private final MDeliveryDestinationRepository mDeliveryDestinationRepository;

    @Autowired
    public MDeliveryDestinationService(MDeliveryDestinationRepository mDeliveryDestinationRepository) {
        this.mDeliveryDestinationRepository = mDeliveryDestinationRepository;
    }

    public List<MDeliveryDestination> findByCompanyNo(Integer companyNo) {
        return this.mDeliveryDestinationRepository.findByCompanyNo(companyNo);
    }

    public List<MDeliveryDestination> findByPartnerNo(Integer partnerNo) {
        return this.mDeliveryDestinationRepository.findByPartnerNo(partnerNo);
    }

    public MDeliveryDestination getByUniqKey(Integer companyNo, String destinationCode) throws Exception {
        List<MDeliveryDestination> list = this.mDeliveryDestinationRepository.findByCompanyNoAndDestinationCode(companyNo, destinationCode);
        if (CollectionUtil.isEmpty(list) || list.size() != 1) {
            return null;
        }
        return list.get(0);
    }

    public MDeliveryDestination getByPK(Integer destinationNo) {
        return this.mDeliveryDestinationRepository.findById(destinationNo).orElseThrow();
    }

    /**
     * 届け先を更新します。
     *
     * @param updateOrder 届け先Entity
     * @return 更新した届け先Entity
     * @throws Exception システム例外
     */
    public MDeliveryDestination update(MDeliveryDestination updateOrder) throws Exception {
        return this.update(this.mDeliveryDestinationRepository, updateOrder);
    }

    /**
     * 届け先を登録します。
     *
     * @param tOrder 届け先Entity
     * @return 登録した届け先Entity
     */
    public MDeliveryDestination insert(MDeliveryDestination tOrder) throws Exception {
        return this.insert(this.mDeliveryDestinationRepository, tOrder);
    }
}
