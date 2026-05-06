package jp.co.oda32.api.batch;

import jp.co.oda32.batch.BatchJobCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {

    // SF-H05: ジョブ定義は BatchJobCatalog に集約 (旧 JOB_DEFINITIONS / ALLOWED_JOBS は削除)。
    // 同種の参照は AccountingStatusService と frontend/accounting-workflow.tsx でも行われるため、
    // 一元管理して 3 重同期コストを排除する。
    private static final Set<String> ALLOWED_JOBS = BatchJobCatalog.allowedJobNames();

    /** SMILE支払取込ステップを含むため inputFile パラメータが必要なジョブ。 */
    private static final Set<String> REQUIRES_INPUT_FILE = Set.of(
            "smilePaymentImport",
            "accountsPayableSummary",
            "accountsPayableVerification"
    );

    /** targetDate(yyyyMMdd) を必須とする財務系ジョブ。 */
    private static final Set<String> REQUIRES_TARGET_DATE = Set.of(
            "accountsPayableAggregation",
            "accountsPayableVerification",
            "accountsPayableSummary",
            "accountsReceivableSummary",
            "purchaseJournalIntegration",
            "salesJournalIntegration"
    );

    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** fromMonth/toMonth (yyyy-MM-dd) を必須とする月範囲ジョブ。 */
    private static final Set<String> REQUIRES_MONTH_RANGE = Set.of(
            "accountsPayableBackfill"
    );

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * targetDate を yyyyMMdd に正規化。
     * 受付形式: yyyyMMdd / yyyy-MM-dd / yyyy/MM/dd。null・空は null を返す。
     */
    private static String normalizeTargetDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String digits = s.replaceAll("[-/]", "");
        if (digits.length() != 8 || !digits.chars().allMatch(Character::isDigit)) return null;
        try {
            LocalDate.parse(digits, YYYY_MM_DD);
            return digits;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    @Configuration
    static class BatchExecutorConfig {
        @Bean(name = "batchTaskExecutor", destroyMethod = "shutdown")
        public ThreadPoolTaskExecutor batchTaskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(5);
            executor.setThreadNamePrefix("batch-");
            executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            executor.initialize();
            return executor;
        }
    }

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final ApplicationContext applicationContext;
    @Qualifier("batchTaskExecutor")
    private final ThreadPoolTaskExecutor batchExecutor;

    @GetMapping("/jobs")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<List<Map<String, Object>>> listJobs() {
        // SF-H05: BatchJobCatalog から各エントリを map 化して返却 (既存 API 互換維持)。
        List<Map<String, Object>> result = BatchJobCatalog.ENTRIES.stream().map(def -> {
            String jobName = def.jobName();
            String beanName = jobName + "Job";
            boolean available = applicationContext.containsBean(beanName);
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("jobName", jobName);
            job.put("category", def.category());
            job.put("description", def.description());
            job.put("requiresShopNo", String.valueOf(def.requiresShopNo()));
            job.put("available", available);
            job.put("requiresInputFile", REQUIRES_INPUT_FILE.contains(jobName));
            job.put("requiresTargetDate", REQUIRES_TARGET_DATE.contains(jobName));
            job.put("requiresMonthRange", REQUIRES_MONTH_RANGE.contains(jobName));
            return job;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status/{jobName}")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobName) {
        var instances = jobExplorer.findJobInstancesByJobName(jobName, 0, 1);
        if (instances.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "NONE", "jobName", jobName));
        }
        var executions = jobExplorer.getJobExecutions(instances.get(0));
        if (executions.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "NONE", "jobName", jobName));
        }
        // 最新の実行を取得（createTime降順でソート）
        var latest = executions.stream()
                .sorted(Comparator.comparing(
                        JobExecution::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .findFirst().orElse(executions.get(0));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobName", jobName);
        result.put("status", latest.getStatus().toString());
        result.put("exitCode", latest.getExitStatus().getExitCode());
        result.put("startTime", latest.getStartTime());
        result.put("endTime", latest.getEndTime());
        if (!"COMPLETED".equals(latest.getExitStatus().getExitCode()) && latest.getExitStatus().getExitDescription() != null) {
            String desc = latest.getExitStatus().getExitDescription();
            result.put("exitMessage", desc.length() > 200 ? desc.substring(0, 200) : desc);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/execute/{jobName}")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<Map<String, String>> execute(
            @PathVariable String jobName,
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String targetDate,
            @RequestParam(required = false) String fromMonth,
            @RequestParam(required = false) String toMonth) {
        if (!ALLOWED_JOBS.contains(jobName)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "許可されていないジョブです"));
        }
        try {
            String beanName = jobName + "Job";
            if (!applicationContext.containsBean(beanName)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "ジョブが見つかりません"));
            }
            Job job = (Job) applicationContext.getBean(beanName);
            JobParametersBuilder params = new JobParametersBuilder();
            params.addLong("time", System.currentTimeMillis());
            if (shopNo != null) {
                params.addLong("shopNo", shopNo.longValue());
            }
            // SMILE支払取込ステップを含むジョブは inputFile パラメータが必要
            if (REQUIRES_INPUT_FILE.contains(jobName)) {
                params.addString("inputFile", "input/smile_payment_import.csv");
            }
            // 財務系ジョブは targetDate(yyyyMMdd) パラメータが必要
            if (REQUIRES_TARGET_DATE.contains(jobName)) {
                String resolved = normalizeTargetDate(targetDate);
                if (resolved == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "targetDate パラメータ(yyyyMMdd または yyyy-MM-dd)が必要です"));
                }
                params.addString("targetDate", resolved);
            }
            // 月範囲ジョブ (backfill 系) は fromMonth/toMonth(yyyy-MM-dd) が必要
            if (REQUIRES_MONTH_RANGE.contains(jobName)) {
                if (fromMonth == null || fromMonth.isBlank() || toMonth == null || toMonth.isBlank()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "fromMonth / toMonth パラメータ(yyyy-MM-dd, 月末日)が必要です"));
                }
                try {
                    LocalDate from = LocalDate.parse(fromMonth, ISO_DATE);
                    LocalDate to = LocalDate.parse(toMonth, ISO_DATE);
                    if (from.isAfter(to)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("message", "fromMonth は toMonth 以前である必要があります"));
                    }
                } catch (DateTimeParseException ex) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "fromMonth / toMonth は yyyy-MM-dd 形式で指定してください"));
                }
                params.addString("fromMonth", fromMonth);
                params.addString("toMonth", toMonth);
            }
            // 非同期で実行（APIは即座にレスポンスを返す）
            var asyncJobParams = params.toJobParameters();
            batchExecutor.execute(() -> {
                try {
                    jobLauncher.run(job, asyncJobParams);
                    log.info("バッチジョブ完了: jobName={}", jobName);
                } catch (Exception ex) {
                    log.error("バッチジョブ実行エラー（非同期）: jobName={}", jobName, ex);
                }
            });
            return ResponseEntity.accepted().body(Map.of("message", "ジョブを起動しました: " + jobName));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("バッチジョブ実行拒否（同時実行数上限）: jobName={}", jobName);
            return ResponseEntity.status(429)
                    .body(Map.of("message", "同時実行数の上限に達しています。しばらく待ってから再実行してください"));
        } catch (Exception e) {
            log.error("バッチジョブ実行エラー: jobName={}", jobName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "ジョブの起動に失敗しました"));
        }
    }
}
