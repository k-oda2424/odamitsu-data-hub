package jp.co.oda32.domain.service.master;

/**
 * 配送担当者マスタのサービスクラス
 *
 * @author k_oda
 * @since 2024/05/04
 */

import jp.co.oda32.domain.model.master.MDeliveryPerson;
import jp.co.oda32.domain.repository.master.MDeliveryPersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MDeliveryPersonService {

    private final MDeliveryPersonRepository mDeliveryPersonRepository;

    public List<MDeliveryPerson> findAll() {
        return mDeliveryPersonRepository.findAll();
    }
}
