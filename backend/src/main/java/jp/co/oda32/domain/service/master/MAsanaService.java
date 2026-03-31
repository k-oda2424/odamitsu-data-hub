package jp.co.oda32.domain.service.master;

/**
 * asanaマスタのサービスクラス
 *
 * @author k_oda
 * @since 2024/05/04
 */

import jp.co.oda32.domain.model.master.MAsana;
import jp.co.oda32.domain.repository.master.MAsanaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MAsanaService {

    @Autowired
    private MAsanaRepository mAsanaRepository;

    public List<MAsana> getAllMAsanaList() {
        return mAsanaRepository.findAll();
    }
}
