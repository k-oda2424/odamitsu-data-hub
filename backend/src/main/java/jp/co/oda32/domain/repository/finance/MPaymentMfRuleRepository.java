package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MPaymentMfRuleRepository extends JpaRepository<MPaymentMfRule, Integer> {

    List<MPaymentMfRule> findByDelFlgOrderByPriorityAscIdAsc(String delFlg);

    Optional<MPaymentMfRule> findByPaymentSupplierCodeAndDelFlg(String paymentSupplierCode, String delFlg);

    Optional<MPaymentMfRule> findBySourceNameAndDelFlg(String sourceName, String delFlg);
}
