package jp.co.oda32.domain.service.finance;

import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.dto.finance.OffsetJournalRuleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * G2-M8: PaymentMfImport の OFFSET 副行貸方科目マスタ ({@link MOffsetJournalRule}) を管理する Service。
 *
 * <p>shop_no + del_flg='0' で UNIQUE のため active 行は shop あたり最大 1 件。
 * Admin UI から create / update / delete を行い、税理士確認結果を再 deploy なしで反映する。
 *
 * <p>{@link AuditLog} で T2 監査証跡対象。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MOffsetJournalRuleService {

    private final MOffsetJournalRuleRepository repository;

    private Integer currentUserNo() {
        try {
            return LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        } catch (Exception e) {
            return null;
        }
    }

    public List<MOffsetJournalRule> findAll() {
        return repository.findByDelFlgOrderByShopNoAsc("0");
    }

    public MOffsetJournalRule findByShopNo(Integer shopNo) {
        return repository.findByShopNoAndDelFlg(shopNo, "0").orElse(null);
    }

    @Transactional
    @AuditLog(table = "m_offset_journal_rule", operation = "INSERT",
            pkExpression = "{'shopNo': #a0.shopNo}",
            captureArgsAsAfter = true)
    public MOffsetJournalRule create(OffsetJournalRuleRequest req) {
        Integer userNo = currentUserNo();
        LocalDateTime now = LocalDateTime.now();
        MOffsetJournalRule e = MOffsetJournalRule.builder()
                .shopNo(req.getShopNo())
                .creditAccount(req.getCreditAccount())
                .creditSubAccount(emptyToNull(req.getCreditSubAccount()))
                .creditDepartment(emptyToNull(req.getCreditDepartment()))
                .creditTaxCategory(req.getCreditTaxCategory())
                .summaryPrefix(req.getSummaryPrefix() == null || req.getSummaryPrefix().isEmpty()
                        ? "相殺／" : req.getSummaryPrefix())
                .delFlg("0")
                .addDateTime(now)
                .addUserNo(userNo)
                .modifyDateTime(now)
                .modifyUserNo(userNo)
                .build();
        return repository.save(e);
    }

    @Transactional
    @AuditLog(table = "m_offset_journal_rule", operation = "UPDATE",
            pkExpression = "{'id': #a0}",
            captureArgsAsAfter = true)
    public MOffsetJournalRule update(Integer id, OffsetJournalRuleRequest req) {
        MOffsetJournalRule e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OFFSET 仕訳マスタが見つかりません: id=" + id));
        e.setShopNo(req.getShopNo());
        e.setCreditAccount(req.getCreditAccount());
        e.setCreditSubAccount(emptyToNull(req.getCreditSubAccount()));
        e.setCreditDepartment(emptyToNull(req.getCreditDepartment()));
        e.setCreditTaxCategory(req.getCreditTaxCategory());
        if (req.getSummaryPrefix() != null && !req.getSummaryPrefix().isEmpty()) {
            e.setSummaryPrefix(req.getSummaryPrefix());
        }
        e.setModifyDateTime(LocalDateTime.now());
        e.setModifyUserNo(currentUserNo());
        return repository.save(e);
    }

    @Transactional
    @AuditLog(table = "m_offset_journal_rule", operation = "DELETE",
            pkExpression = "{'id': #a0}",
            captureArgsAsAfter = true)
    public void delete(Integer id) {
        MOffsetJournalRule e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OFFSET 仕訳マスタが見つかりません: id=" + id));
        e.setDelFlg("1");
        e.setModifyDateTime(LocalDateTime.now());
        e.setModifyUserNo(currentUserNo());
        repository.save(e);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
