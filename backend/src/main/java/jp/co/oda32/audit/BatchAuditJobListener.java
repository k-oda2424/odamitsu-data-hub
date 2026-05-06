package jp.co.oda32.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * G3-M8 (2026-05-06): Spring Batch Job 起動/終了を {@code finance_audit_log} に
 * <b>summary</b> 記録する Listener。
 *
 * <p>従来 {@link AuditLog @AuditLog} AOP ({@link FinanceAuditAspect}) は web プロファイル限定
 * (= SecurityContext / HttpServletRequest が前提) で、バッチ経由の集計再生成 / 検証 /
 * 価格反映等の silent UPDATE が監査対象外だった。本 Listener は <b>Job 単位</b>で
 * 1 起動あたり 2 row (beforeJob + afterJob) を {@link FinanceAuditWriter} 経由で
 * 別 tx ({@code REQUIRES_NEW}) 書込みする。tasklet 個別の細粒度 audit ではなく
 * サマリーであり、業務 tx の rollback で audit が消えないことを保証する。
 *
 * <h3>記録仕様</h3>
 * <ul>
 *   <li>{@code actor_type = 'BATCH'} (USER / SYSTEM / BATCH の 3 種で web AOP と区別)</li>
 *   <li>{@code actor_user_no} = jobParameters の {@code userNo} があれば使用、なければ NULL</li>
 *   <li>{@code target_table} = jobName (例: {@code accountsPayableSummary})</li>
 *   <li>{@code operation} = {@code batch_run_started} / {@code batch_run_finished}</li>
 *   <li>{@code target_pk} = {@code {jobName, jobInstanceId, params}} の安定 JSON</li>
 *   <li>{@code before_values} = jobParameters snapshot (起動時のみ)</li>
 *   <li>{@code after_values} = exitCode / status / start/end / read/write/skip count (終了時のみ)</li>
 *   <li>{@code reason} = exitDescription (FAILED 時) または exitCode</li>
 * </ul>
 *
 * <h3>失敗時挙動 (fail-open)</h3>
 * audit 書込み失敗 (= DB 障害等) は WARN ログのみ出力し、業務 (= バッチ自身) は継続させる。
 * 監査は副次的な責務であり、業務に影響を与えてはならない。
 *
 * <h3>適用方法</h3>
 * 各 Job への配線は {@link BatchAuditListenerAutoRegistrar} (BeanPostProcessor) が
 * 自動的に行う。各 *JobConfig.java の編集は不要。
 *
 * @see FinanceAuditAspect web 経路の AOP audit
 * @see FinanceAuditWriter REQUIRES_NEW で別 tx 書き込み
 * @see BatchAuditListenerAutoRegistrar 全 Job への自動配線 (BeanPostProcessor)
 * @since 2026-05-06 (G3-M8)
 */
@Component
@Log4j2
public class BatchAuditJobListener implements JobExecutionListener {

    private static final int REASON_MAX_LEN = 500;

    private final FinanceAuditWriter writer;
    private final ObjectMapper auditMapper;

    public BatchAuditJobListener(FinanceAuditWriter writer) {
        this.writer = writer;
        // 専用 ObjectMapper: JavaTime + 循環参照を出さず空に倒す (FinanceAuditAspect と同方針)
        this.auditMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.FAIL_ON_SELF_REFERENCES);
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = safeJobName(jobExecution);
        try {
            writer.write(
                    jobName,
                    "batch_run_started",
                    extractUserNo(jobExecution),
                    "BATCH",
                    buildTargetPk(jobName, jobExecution),
                    buildJobParametersSnapshot(jobExecution),
                    null,
                    null,
                    null,
                    null);
        } catch (Exception e) {
            log.warn("[batch-audit] beforeJob 監査ログ書込みに失敗 (業務継続): job={}, err={}",
                    jobName, e.toString());
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = safeJobName(jobExecution);
        try {
            writer.write(
                    jobName,
                    "batch_run_finished",
                    extractUserNo(jobExecution),
                    "BATCH",
                    buildTargetPk(jobName, jobExecution),
                    null,
                    buildExitSnapshot(jobExecution),
                    buildReason(jobExecution),
                    null,
                    null);
        } catch (Exception e) {
            log.warn("[batch-audit] afterJob 監査ログ書込みに失敗 (業務継続): job={}, err={}",
                    jobName, e.toString());
        }
    }

    /** {@code jobParameters.userNo} があれば actor として記録 (= 手動起動時の admin 等)。 */
    private Integer extractUserNo(JobExecution jobExecution) {
        JobParameter<?> userNoParam = jobExecution.getJobParameters().getParameters().get("userNo");
        if (userNoParam == null || userNoParam.getValue() == null) {
            return null;
        }
        Object value = userNoParam.getValue();
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** target_pk: jobName + jobInstanceId + jobParameters の安定 JSON。 */
    private JsonNode buildTargetPk(String jobName, JobExecution jobExecution) {
        ObjectNode pk = auditMapper.createObjectNode();
        pk.put("jobName", jobName);
        if (jobExecution.getJobInstance() != null) {
            pk.put("jobInstanceId", jobExecution.getJobInstance().getInstanceId());
        }
        ObjectNode params = pk.putObject("params");
        for (Map.Entry<String, JobParameter<?>> entry
                : jobExecution.getJobParameters().getParameters().entrySet()) {
            params.put(entry.getKey(), stringValue(entry.getValue()));
        }
        return pk;
    }

    /** 起動時 snapshot: jobParameters のみ。 */
    private JsonNode buildJobParametersSnapshot(JobExecution jobExecution) {
        ObjectNode node = auditMapper.createObjectNode();
        for (Map.Entry<String, JobParameter<?>> entry
                : jobExecution.getJobParameters().getParameters().entrySet()) {
            node.put(entry.getKey(), stringValue(entry.getValue()));
        }
        return node;
    }

    /** 終了時 snapshot: exitStatus / status / 時刻 / step 集計件数。 */
    private JsonNode buildExitSnapshot(JobExecution jobExecution) {
        ObjectNode node = auditMapper.createObjectNode();
        node.put("exitCode", jobExecution.getExitStatus().getExitCode());
        node.put("status", jobExecution.getStatus().name());
        node.put("startTime", String.valueOf(jobExecution.getStartTime()));
        node.put("endTime", String.valueOf(jobExecution.getEndTime()));

        long readCount = jobExecution.getStepExecutions().stream()
                .mapToLong(s -> s.getReadCount()).sum();
        long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(s -> s.getWriteCount()).sum();
        long skipCount = jobExecution.getStepExecutions().stream()
                .mapToLong(s -> s.getSkipCount()).sum();
        node.put("totalReadCount", readCount);
        node.put("totalWriteCount", writeCount);
        node.put("totalSkipCount", skipCount);
        node.put("stepCount", jobExecution.getStepExecutions().size());
        return node;
    }

    /** reason: exitDescription があれば優先、なければ exitCode。長い stack trace は切り詰める。 */
    private String buildReason(JobExecution jobExecution) {
        String desc = jobExecution.getExitStatus().getExitDescription();
        if (desc != null && !desc.isBlank()) {
            return desc.length() > REASON_MAX_LEN ? desc.substring(0, REASON_MAX_LEN) + "..." : desc;
        }
        return jobExecution.getExitStatus().getExitCode();
    }

    /** JobInstance が無いタイミングでも例外を出さず "(unknown)" を返す。 */
    private String safeJobName(JobExecution jobExecution) {
        if (jobExecution.getJobInstance() != null
                && jobExecution.getJobInstance().getJobName() != null) {
            return jobExecution.getJobInstance().getJobName();
        }
        return "(unknown)";
    }

    /** JobParameter の値文字列化 (NULL safe)。 */
    private String stringValue(JobParameter<?> param) {
        if (param == null || param.getValue() == null) {
            return null;
        }
        return String.valueOf(param.getValue());
    }
}
