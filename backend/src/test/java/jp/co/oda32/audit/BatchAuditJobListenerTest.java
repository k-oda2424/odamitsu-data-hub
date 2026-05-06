package jp.co.oda32.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * G3-M8: {@link BatchAuditJobListener} の単体テスト。
 *
 * <p>{@link FinanceAuditWriter} を mock し、beforeJob / afterJob で想定どおりの引数で
 * {@code write} が呼ばれること、および書込み失敗時に例外が伝播せず業務継続することを検証する。
 */
class BatchAuditJobListenerTest {

    private FinanceAuditWriter writer;
    private BatchAuditJobListener listener;

    @BeforeEach
    void setUp() {
        writer = mock(FinanceAuditWriter.class);
        listener = new BatchAuditJobListener(writer);
    }

    @Test
    void beforeJob_records_started_with_BATCH_actor_and_jobParameters_snapshot() {
        JobExecution exec = newJobExecution("accountsPayableSummary", 100L,
                Map.of("shopNo", new JobParameter<>("1", String.class, true)));

        listener.beforeJob(exec);

        ArgumentCaptor<JsonNode> targetPk = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> beforeValues = ArgumentCaptor.forClass(JsonNode.class);
        verify(writer).write(
                eq("accountsPayableSummary"),
                eq("batch_run_started"),
                isNull(),               // userNo not provided
                eq("BATCH"),
                targetPk.capture(),
                beforeValues.capture(),
                isNull(),               // afterValues
                isNull(),               // reason
                isNull(),               // sourceIp
                isNull());              // userAgent
        assertThat(targetPk.getValue().get("jobName").asText()).isEqualTo("accountsPayableSummary");
        assertThat(targetPk.getValue().get("jobInstanceId").asLong()).isEqualTo(100L);
        assertThat(targetPk.getValue().get("params").get("shopNo").asText()).isEqualTo("1");
        assertThat(beforeValues.getValue().get("shopNo").asText()).isEqualTo("1");
    }

    @Test
    void beforeJob_extracts_userNo_when_provided_as_jobParameter() {
        Map<String, JobParameter<?>> params = new LinkedHashMap<>();
        params.put("userNo", new JobParameter<>(42L, Long.class, true));
        JobExecution exec = newJobExecution("smilePaymentImport", 1L, params);

        listener.beforeJob(exec);

        verify(writer).write(
                eq("smilePaymentImport"),
                eq("batch_run_started"),
                eq(42),                 // userNo extracted
                eq("BATCH"),
                any(JsonNode.class),
                any(JsonNode.class),
                isNull(),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void afterJob_records_finished_with_exit_snapshot_and_reason() {
        JobExecution exec = newJobExecution("goodsFileImport", 5L, Map.of());
        exec.setExitStatus(ExitStatus.COMPLETED);
        exec.setStatus(BatchStatus.COMPLETED);
        exec.setStartTime(LocalDateTime.of(2026, 5, 6, 10, 0));
        exec.setEndTime(LocalDateTime.of(2026, 5, 6, 10, 1));

        StepExecution step = new StepExecution("step1", exec);
        step.setReadCount(1000);
        step.setWriteCount(950);
        step.setReadSkipCount(20);
        step.setProcessSkipCount(20);
        step.setWriteSkipCount(10);
        exec.addStepExecutions(java.util.List.of(step));

        listener.afterJob(exec);

        ArgumentCaptor<JsonNode> afterValues = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(writer).write(
                eq("goodsFileImport"),
                eq("batch_run_finished"),
                isNull(),
                eq("BATCH"),
                any(JsonNode.class),
                isNull(),               // beforeValues null on after
                afterValues.capture(),
                reason.capture(),
                isNull(),
                isNull());

        JsonNode after = afterValues.getValue();
        assertThat(after.get("exitCode").asText()).isEqualTo("COMPLETED");
        assertThat(after.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(after.get("totalReadCount").asLong()).isEqualTo(1000L);
        assertThat(after.get("totalWriteCount").asLong()).isEqualTo(950L);
        assertThat(after.get("totalSkipCount").asLong()).isEqualTo(50L);
        assertThat(after.get("stepCount").asInt()).isEqualTo(1);
        assertThat(reason.getValue()).isEqualTo("COMPLETED"); // exitDescription空 → exitCodeフォールバック
    }

    @Test
    void afterJob_truncates_long_exit_description() {
        JobExecution exec = newJobExecution("purchaseFileImport", 1L, Map.of());
        String hugeDesc = "boom".repeat(500); // 2000 chars
        exec.setExitStatus(ExitStatus.FAILED.addExitDescription(hugeDesc));

        listener.afterJob(exec);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(writer).write(
                eq("purchaseFileImport"),
                eq("batch_run_finished"),
                isNull(),
                eq("BATCH"),
                any(JsonNode.class),
                isNull(),
                any(JsonNode.class),
                reason.capture(),
                isNull(),
                isNull());

        // 切り詰め: 末尾は "..." (REASON_MAX_LEN=500 + "...")
        assertThat(reason.getValue()).hasSize(503);
        assertThat(reason.getValue()).endsWith("...");
    }

    @Test
    void beforeJob_swallows_writer_exception_and_keeps_job_running() {
        JobExecution exec = newJobExecution("bCartCategorySync", 1L, Map.of());
        doThrow(new RuntimeException("DB down"))
                .when(writer).write(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        // 例外を伝播させない (= バッチ自身を失敗させない)
        listener.beforeJob(exec);

        verify(writer).write(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoMoreInteractions(writer);
    }

    @Test
    void afterJob_swallows_writer_exception_and_keeps_job_running() {
        JobExecution exec = newJobExecution("bCartCategorySync", 1L, Map.of());
        doThrow(new RuntimeException("DB down"))
                .when(writer).write(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        listener.afterJob(exec);

        verify(writer).write(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoMoreInteractions(writer);
    }

    @Test
    void unknown_jobInstance_falls_back_to_unknown_label() {
        // JobInstance を渡さないコンストラクタが SimpleJob 等で発生し得るケース (極端)
        JobExecution exec = new JobExecution(7L, new JobParameters());
        // exec.jobInstance は null

        listener.beforeJob(exec);

        verify(writer).write(
                eq("(unknown)"),
                eq("batch_run_started"),
                isNull(),
                eq("BATCH"),
                any(JsonNode.class),
                any(JsonNode.class),
                isNull(),
                isNull(),
                isNull(),
                isNull());
    }

    // -- helpers --

    private JobExecution newJobExecution(String jobName, long instanceId,
                                         Map<String, JobParameter<?>> params) {
        JobInstance instance = new JobInstance(instanceId, jobName);
        JobExecution exec = new JobExecution(instance, new JobParameters(params));
        return exec;
    }
}
