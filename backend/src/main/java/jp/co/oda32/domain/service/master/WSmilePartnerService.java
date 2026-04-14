package jp.co.oda32.domain.service.master;

/**
 * WSmilePartnerのサービスクラス
 *
 * @author k_oda
 * @since 2024/06/12
 */

import jp.co.oda32.domain.model.master.WSmilePartner;
import jp.co.oda32.domain.repository.master.WSmilePartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WSmilePartnerService {

    private final WSmilePartnerRepository wSmilePartnerRepository;

    public List<WSmilePartner> findAll() {
        return wSmilePartnerRepository.findAll();
    }

    public WSmilePartner save(WSmilePartner wSmilePartner) {
        return wSmilePartnerRepository.save(wSmilePartner);
    }

    public void truncateTable() {
        wSmilePartnerRepository.truncateTable();
    }

//    public List<WSmilePartner> findRecordsForUpdate() {
//        return this.wSmilePartnerRepository.findRecordsForUpdate();
//    }
}
