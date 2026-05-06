package jp.co.oda32.audit;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G3-M8: {@link BatchAuditListenerAutoRegistrar} の単体テスト。
 *
 * <p>{@link Job} Bean が初期化された際に {@link BatchAuditJobListener} が
 * {@link AbstractJob#registerJobExecutionListener} 経由で配線されることを検証する。
 */
class BatchAuditListenerAutoRegistrarTest {

    @Test
    void registers_listener_on_AbstractJob_bean() {
        BatchAuditJobListener listener = mock(BatchAuditJobListener.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BatchAuditJobListener> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(listener);
        BatchAuditListenerAutoRegistrar registrar = new BatchAuditListenerAutoRegistrar(provider);

        AbstractJob job = mock(AbstractJob.class);
        when(job.getName()).thenReturn("testJob");

        Object result = registrar.postProcessAfterInitialization(job, "testJob");

        // BeanPostProcessor は元の Bean をそのまま返すこと
        assert result == job;
        verify(job).registerJobExecutionListener(same(listener));
    }

    @Test
    void skips_non_Job_beans() {
        BatchAuditJobListener listener = mock(BatchAuditJobListener.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BatchAuditJobListener> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(listener);
        BatchAuditListenerAutoRegistrar registrar = new BatchAuditListenerAutoRegistrar(provider);

        Object randomBean = new Object();
        Object result = registrar.postProcessAfterInitialization(randomBean, "random");

        assert result == randomBean;
    }

    @Test
    void noop_when_listener_bean_not_available() {
        @SuppressWarnings("unchecked")
        ObjectProvider<BatchAuditJobListener> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        BatchAuditListenerAutoRegistrar registrar = new BatchAuditListenerAutoRegistrar(provider);

        AbstractJob job = mock(AbstractJob.class);
        Object result = registrar.postProcessAfterInitialization(job, "testJob");

        assert result == job;
        verify(job, never()).registerJobExecutionListener(any(JobExecutionListener.class));
    }

    @Test
    void warns_but_does_not_fail_for_non_AbstractJob_implementation() {
        BatchAuditJobListener listener = mock(BatchAuditJobListener.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BatchAuditJobListener> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(listener);
        BatchAuditListenerAutoRegistrar registrar = new BatchAuditListenerAutoRegistrar(provider);

        Job customJob = mock(Job.class);
        when(customJob.getName()).thenReturn("customJob");
        Object result = registrar.postProcessAfterInitialization(customJob, "customJob");

        assert result == customJob;
        // AbstractJob ではないので何も起きない (= 例外伝播せず、bean はそのまま返る)
    }
}
