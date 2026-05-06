package jp.co.oda32.domain.service.audit;

import jp.co.oda32.domain.repository.audit.FinanceAuditLogRepository;
import jp.co.oda32.domain.repository.master.LoginUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G3-M5: {@link AuditLogQueryService#distinctTargetTables()} / {@link AuditLogQueryService#distinctOperations()} が
 * Repository の DB レベル DISTINCT クエリ ({@code findDistinctTargetTables} / {@code findDistinctOperations})
 * に委譲し、{@code findAll()} で全件ロードしないことを検証する。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {

    @Mock FinanceAuditLogRepository repository;
    @Mock LoginUserRepository loginUserRepository;
    @InjectMocks AuditLogQueryService service;

    @Test
    void distinctTargetTables_delegates_to_repository_distinct_query() {
        List<String> stub = List.of("t_accounts_payable_summary", "t_consistency_review");
        when(repository.findDistinctTargetTables()).thenReturn(stub);

        List<String> result = service.distinctTargetTables();

        assertThat(result).containsExactlyElementsOf(stub);
        verify(repository).findDistinctTargetTables();
        verify(repository, never()).findAll();
    }

    @Test
    void distinctTargetTables_returns_empty_when_repository_empty() {
        when(repository.findDistinctTargetTables()).thenReturn(Collections.emptyList());

        List<String> result = service.distinctTargetTables();

        assertThat(result).isEmpty();
        verify(repository).findDistinctTargetTables();
        verify(repository, never()).findAll();
    }

    @Test
    void distinctOperations_delegates_to_repository_distinct_query() {
        List<String> stub = List.of("verify", "manual_lock", "force_apply");
        when(repository.findDistinctOperations()).thenReturn(stub);

        List<String> result = service.distinctOperations();

        assertThat(result).containsExactlyElementsOf(stub);
        verify(repository).findDistinctOperations();
        verify(repository, never()).findAll();
    }

    @Test
    void distinctOperations_returns_empty_when_repository_empty() {
        when(repository.findDistinctOperations()).thenReturn(Collections.emptyList());

        List<String> result = service.distinctOperations();

        assertThat(result).isEmpty();
        verify(repository).findDistinctOperations();
        verify(repository, never()).findAll();
    }
}
