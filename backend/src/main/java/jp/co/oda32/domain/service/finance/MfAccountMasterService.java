package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MfAccountMasterService {

    private final MfAccountMasterRepository mfAccountMasterRepository;

    public List<MfAccountMaster> findByFinancialStatementItemAndAccountName(String financialStatementItem, String accountName) {
        return mfAccountMasterRepository.findByFinancialStatementItemAndAccountNameAndSearchKeyIsNotNull(financialStatementItem, accountName);
    }

    public Map<String, MfAccountMaster> findByFinancialStatementItemAndAccountNameMappedBySearchKey(String financialStatementItem, String accountName) {
        return findByFinancialStatementItemAndAccountName(financialStatementItem, accountName)
                .stream()
                .collect(Collectors.toMap(MfAccountMaster::getSearchKey, accountMaster -> accountMaster));
    }
}
