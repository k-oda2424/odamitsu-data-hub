package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MfAccountMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MfAccountMasterRepository extends JpaRepository<MfAccountMaster, Long> {

    List<MfAccountMaster> findByFinancialStatementItemAndAccountNameAndSearchKeyIsNotNull(String financialStatementItem, String accountName);
}
