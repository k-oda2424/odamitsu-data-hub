package jp.co.oda32.domain.service.master;

/**
 * asanaマスタのサービスクラス
 *
 * @author k_oda
 * @since 2024/05/04
 */

import jp.co.oda32.domain.model.master.MAsana;
import jp.co.oda32.domain.repository.master.MAsanaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MAsanaService {

    private final MAsanaRepository mAsanaRepository;

    public List<MAsana> getAllMAsanaList() {
        return mAsanaRepository.findAll();
    }
}
