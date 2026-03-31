package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfSubAccount;
import jp.co.oda32.domain.repository.finance.MMfSubAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * マネーフォワード補助科目マスタテーブルのサービスクラス
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Service
public class MMfSubAccountService {

    private final MMfSubAccountRepository repository;

    @Autowired
    public MMfSubAccountService(MMfSubAccountRepository repository) {
        this.repository = repository;
    }

    public List<MMfSubAccount> findAll() {
        return repository.findAll();
    }

    public Optional<MMfSubAccount> findByPartnerCodeAndSubAccountName(String partnerCode, String subAccountName) {
        return repository.findByPartnerCodeAndSubAccountName(partnerCode, subAccountName);
    }

    public MMfSubAccount save(MMfSubAccount subAccount) {
        return repository.save(subAccount);
    }

    // 他のメソッドもここで定義できます
}
