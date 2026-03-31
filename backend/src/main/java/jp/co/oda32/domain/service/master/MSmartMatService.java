package jp.co.oda32.domain.service.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MSmartMat;
import jp.co.oda32.domain.repository.master.MSmartMatRepository;
import jp.co.oda32.domain.service.CustomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * スマートマット管理マスタEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2020/01/09
 */
@Service
public class MSmartMatService extends CustomService {
    private final MSmartMatRepository mSmartMatRepository;
//    private MSmartMatSpecification mShopSpecification = new MSmartMatSpecification();

    @Autowired
    public MSmartMatService(MSmartMatRepository mSmartMatRepository) {
        this.mSmartMatRepository = mSmartMatRepository;
    }

    public List<MSmartMat> findAll() {
        return mSmartMatRepository.findByDelFlg(Flag.NO.getValue());
    }

}
