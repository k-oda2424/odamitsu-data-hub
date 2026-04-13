package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfJournalRule;
import jp.co.oda32.domain.repository.finance.MMfJournalRuleRepository;
import jp.co.oda32.dto.finance.cashbook.MfJournalRuleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MMfJournalRuleService {

    private final MMfJournalRuleRepository repository;

    @Transactional(readOnly = true)
    public List<MMfJournalRule> findAll() {
        return repository.findByDelFlgOrderByDescriptionCAscPriorityAsc("0");
    }

    @Transactional
    public MMfJournalRule create(MfJournalRuleRequest req) {
        MMfJournalRule e = MMfJournalRule.builder()
                .descriptionC(req.getDescriptionC())
                .descriptionDKeyword(emptyToNull(req.getDescriptionDKeyword()))
                .priority(req.getPriority())
                .amountSource(req.getAmountSource())
                .debitAccount(req.getDebitAccount())
                .debitSubAccount(nz(req.getDebitSubAccount()))
                .debitDepartment(nz(req.getDebitDepartment()))
                .debitTaxResolver(req.getDebitTaxResolver())
                .creditAccount(req.getCreditAccount())
                .creditSubAccount(nz(req.getCreditSubAccount()))
                .creditSubAccountTemplate(nz(req.getCreditSubAccountTemplate()))
                .creditDepartment(nz(req.getCreditDepartment()))
                .creditTaxResolver(req.getCreditTaxResolver())
                .summaryTemplate(req.getSummaryTemplate())
                .requiresClientMapping(req.getRequiresClientMapping())
                .delFlg("0")
                .addDateTime(LocalDateTime.now())
                .build();
        return repository.save(e);
    }

    @Transactional
    public MMfJournalRule update(Integer id, MfJournalRuleRequest req) {
        MMfJournalRule e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルールが見つかりません: id=" + id));
        e.setDescriptionC(req.getDescriptionC());
        e.setDescriptionDKeyword(emptyToNull(req.getDescriptionDKeyword()));
        e.setPriority(req.getPriority());
        e.setAmountSource(req.getAmountSource());
        e.setDebitAccount(req.getDebitAccount());
        e.setDebitSubAccount(nz(req.getDebitSubAccount()));
        e.setDebitDepartment(nz(req.getDebitDepartment()));
        e.setDebitTaxResolver(req.getDebitTaxResolver());
        e.setCreditAccount(req.getCreditAccount());
        e.setCreditSubAccount(nz(req.getCreditSubAccount()));
        e.setCreditSubAccountTemplate(nz(req.getCreditSubAccountTemplate()));
        e.setCreditDepartment(nz(req.getCreditDepartment()));
        e.setCreditTaxResolver(req.getCreditTaxResolver());
        e.setSummaryTemplate(req.getSummaryTemplate());
        e.setRequiresClientMapping(req.getRequiresClientMapping());
        e.setModifyDateTime(LocalDateTime.now());
        return repository.save(e);
    }

    @Transactional
    public void delete(Integer id) {
        MMfJournalRule e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルールが見つかりません: id=" + id));
        e.setDelFlg("1");
        e.setModifyDateTime(LocalDateTime.now());
        repository.save(e);
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }
}
