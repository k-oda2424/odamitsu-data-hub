package jp.co.oda32.domain.service.master;

/**
 * 配送担当者マスタのサービスクラス
 *
 * @author k_oda
 * @since 2024/05/04
 */

import jp.co.oda32.domain.model.master.MDeliveryPerson;
import jp.co.oda32.domain.repository.master.MDeliveryPersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MDeliveryPersonService {

    @Autowired
    private MDeliveryPersonRepository mDeliveryPersonRepository;

    public List<MDeliveryPerson> findAll() {
        return mDeliveryPersonRepository.findAll();
    }
}
