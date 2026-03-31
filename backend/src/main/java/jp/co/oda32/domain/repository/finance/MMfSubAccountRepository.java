package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MMfSubAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * マネーフォワード補助科目マスタテーブルのリポジトリクラス
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Repository
public interface MMfSubAccountRepository extends JpaRepository<MMfSubAccount, Long> {
    Optional<MMfSubAccount> findByPartnerCodeAndSubAccountName(String partnerCode, String subAccountName);
}
