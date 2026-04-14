package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentMfRuleService {

    private final MPaymentMfRuleRepository repository;

    public List<MPaymentMfRule> findAll() {
        return repository.findByDelFlgOrderByPriorityAscIdAsc("0");
    }

    @Transactional
    public MPaymentMfRule create(PaymentMfRuleRequest req, Integer userNo) {
        MPaymentMfRule rule = MPaymentMfRule.builder()
                .sourceName(req.getSourceName())
                .paymentSupplierCode(blankToNull(req.getPaymentSupplierCode()))
                .ruleKind(req.getRuleKind())
                .debitAccount(req.getDebitAccount())
                .debitSubAccount(blankToNull(req.getDebitSubAccount()))
                .debitDepartment(blankToNull(req.getDebitDepartment()))
                .debitTaxCategory(req.getDebitTaxCategory())
                .creditAccount(defaultStr(req.getCreditAccount(), "資金複合"))
                .creditSubAccount(blankToNull(req.getCreditSubAccount()))
                .creditDepartment(blankToNull(req.getCreditDepartment()))
                .creditTaxCategory(defaultStr(req.getCreditTaxCategory(), "対象外"))
                .summaryTemplate(req.getSummaryTemplate())
                .tag(blankToNull(req.getTag()))
                .priority(req.getPriority() == null ? 100 : req.getPriority())
                .delFlg("0")
                .addDateTime(LocalDateTime.now())
                .addUserNo(userNo)
                .build();
        return repository.save(rule);
    }

    @Transactional
    public MPaymentMfRule update(Integer id, PaymentMfRuleRequest req, Integer userNo) {
        MPaymentMfRule r = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルールが見つかりません: " + id));
        r.setSourceName(req.getSourceName());
        r.setPaymentSupplierCode(blankToNull(req.getPaymentSupplierCode()));
        r.setRuleKind(req.getRuleKind());
        r.setDebitAccount(req.getDebitAccount());
        r.setDebitSubAccount(blankToNull(req.getDebitSubAccount()));
        r.setDebitDepartment(blankToNull(req.getDebitDepartment()));
        r.setDebitTaxCategory(req.getDebitTaxCategory());
        r.setCreditAccount(defaultStr(req.getCreditAccount(), "資金複合"));
        r.setCreditSubAccount(blankToNull(req.getCreditSubAccount()));
        r.setCreditDepartment(blankToNull(req.getCreditDepartment()));
        r.setCreditTaxCategory(defaultStr(req.getCreditTaxCategory(), "対象外"));
        r.setSummaryTemplate(req.getSummaryTemplate());
        r.setTag(blankToNull(req.getTag()));
        if (req.getPriority() != null) r.setPriority(req.getPriority());
        r.setModifyDateTime(LocalDateTime.now());
        r.setModifyUserNo(userNo);
        return repository.save(r);
    }

    @Transactional
    public void delete(Integer id, Integer userNo) {
        MPaymentMfRule r = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルールが見つかりません: " + id));
        r.setDelFlg("1");
        r.setModifyDateTime(LocalDateTime.now());
        r.setModifyUserNo(userNo);
        repository.save(r);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private static String defaultStr(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }
}
