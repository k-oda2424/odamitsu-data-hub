package jp.co.oda32.domain.service.audit;

import jakarta.persistence.criteria.Predicate;
import jp.co.oda32.annotation.SkipShopCheck;
import jp.co.oda32.domain.model.audit.FinanceAuditLog;
import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.repository.audit.FinanceAuditLogRepository;
import jp.co.oda32.domain.repository.master.LoginUserRepository;
import jp.co.oda32.dto.audit.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * T2: 監査ログ閲覧 (admin only) Service。
 * <p>
 * Specification で動的フィルタ (actor / operation / target_table / 日付範囲) を組み、
 * actor_user_no に対応する m_login_user.user_name を batch JOIN で解決する (N+1 回避)。
 *
 * @since 2026-05-04 (T2)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final FinanceAuditLogRepository repository;
    private final LoginUserRepository loginUserRepository;

    @SkipShopCheck
    public Page<AuditLogResponse> search(
            Integer actorUserNo,
            String operation,
            String targetTable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {
        Specification<FinanceAuditLog> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (actorUserNo != null) preds.add(cb.equal(root.get("actorUserNo"), actorUserNo));
            if (operation != null && !operation.isBlank()) preds.add(cb.equal(root.get("operation"), operation));
            if (targetTable != null && !targetTable.isBlank()) preds.add(cb.equal(root.get("targetTable"), targetTable));
            if (fromDate != null) preds.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), fromDate));
            if (toDate != null) preds.add(cb.lessThanOrEqualTo(root.get("occurredAt"), toDate));
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Page<FinanceAuditLog> page = repository.findAll(spec, pageable);
        Map<Integer, String> nameMap = resolveUserNames(page.getContent());
        return page.map(e -> AuditLogResponse.summaryFrom(e, nameMap.get(e.getActorUserNo())));
    }

    @SkipShopCheck
    public Optional<AuditLogResponse> findDetail(Long id) {
        return repository.findById(id).map(e -> {
            String name = e.getActorUserNo() != null
                    ? loginUserRepository.findById(e.getActorUserNo()).map(MLoginUser::getUserName).orElse(null)
                    : null;
            return AuditLogResponse.from(e, name);
        });
    }

    /**
     * distinct な target_table 一覧 (フィルタ UI セレクトボックス用)。
     * <p>
     * G3-M5: 旧実装は {@code findAll().stream()...distinct()} で全件ロード後に JVM 内 distinct していたため
     * audit_log 累積で線形劣化していた。Repository に DB レベル DISTINCT クエリを追加して委譲する
     * (idx_finance_audit_log_target が効く)。
     */
    @SkipShopCheck
    public List<String> distinctTargetTables() {
        return repository.findDistinctTargetTables();
    }

    /**
     * distinct な operation 一覧 (フィルタ UI セレクトボックス用)。
     * <p>
     * G3-M5: 同上。DB レベル DISTINCT で効率化。
     */
    @SkipShopCheck
    public List<String> distinctOperations() {
        return repository.findDistinctOperations();
    }

    private Map<Integer, String> resolveUserNames(List<FinanceAuditLog> rows) {
        Set<Integer> userNos = rows.stream()
                .map(FinanceAuditLog::getActorUserNo)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (userNos.isEmpty()) return Map.of();
        Map<Integer, String> map = new HashMap<>();
        loginUserRepository.findAllById(userNos)
                .forEach(u -> map.put(u.getLoginUserNo(), u.getUserName()));
        return map;
    }
}
