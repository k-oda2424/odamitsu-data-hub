package jp.co.oda32.api.batch;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {

    private static final List<Map<String, String>> JOB_DEFINITIONS = List.of(
            Map.of("jobName", "bCartOrderImport", "category", "B-CART連携", "description", "新規受注取込", "requiresShopNo", "false"),
            Map.of("jobName", "smileOrderFileImport", "category", "B-CART連携", "description", "売上明細取込", "requiresShopNo", "true"),
            Map.of("jobName", "bCartLogisticsCsvExport", "category", "B-CART連携", "description", "出荷実績CSV出力", "requiresShopNo", "false"),
            Map.of("jobName", "bCartMemberUpdate", "category", "B-CART連携", "description", "新規会員取込", "requiresShopNo", "false"),
            Map.of("jobName", "bCartProductsImport", "category", "B-CART連携", "description", "商品マスタ同期", "requiresShopNo", "false"),
            Map.of("jobName", "bCartCategorySync", "category", "B-CART連携", "description", "カテゴリマスタ同期", "requiresShopNo", "false"),
            Map.of("jobName", "bCartCategoryUpdate", "category", "B-CART連携", "description", "カテゴリマスタ反映", "requiresShopNo", "false"),
            Map.of("jobName", "bCartProductDescriptionUpdate", "category", "B-CART連携", "description", "商品説明反映", "requiresShopNo", "false"),
            Map.of("jobName", "goodsFileImport", "category", "マスタ取込", "description", "SMILE商品マスタCSV取込", "requiresShopNo", "true"),
            Map.of("jobName", "purchaseFileImport", "category", "SMILE取込", "description", "SMILE仕入ファイル取込", "requiresShopNo", "true"),
            Map.of("jobName", "smilePaymentImport", "category", "SMILE取込", "description", "SMILE支払情報取込", "requiresShopNo", "true"),
            Map.of("jobName", "accountsPayableAggregation", "category", "買掛金", "description", "買掛金集計", "requiresShopNo", "false"),
            Map.of("jobName", "accountsPayableVerification", "category", "買掛金", "description", "買掛金検証", "requiresShopNo", "false"),
            Map.of("jobName", "accountsPayableSummary", "category", "買掛金", "description", "買掛金サマリ", "requiresShopNo", "false"),
            Map.of("jobName", "accountsReceivableSummary", "category", "売掛金", "description", "売掛金サマリ", "requiresShopNo", "false"),
            Map.of("jobName", "purchaseJournalIntegration", "category", "仕訳連携", "description", "買掛仕入CSV出力（マネーフォワード連携）", "requiresShopNo", "false"),
            Map.of("jobName", "salesJournalIntegration", "category", "仕訳連携", "description", "売掛売上CSV出力（マネーフォワード連携）", "requiresShopNo", "false"),
            Map.of("jobName", "partnerPriceChangePlanCreate", "category", "見積管理", "description", "得意先価格変更予定作成・見積自動生成", "requiresShopNo", "false")
    );

    private static final Set<String> ALLOWED_JOBS = Set.copyOf(
            JOB_DEFINITIONS.stream().map(d -> d.get("jobName")).toList()
    );

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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listJobs() {
        List<Map<String, Object>> result = JOB_DEFINITIONS.stream().map(def -> {
            String beanName = def.get("jobName") + "Job";
            boolean available = applicationContext.containsBean(beanName);
            Map<String, Object> job = new LinkedHashMap<>(def);
            job.put("available", available);
            return job;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status/{jobName}")
    @PreAuthorize("hasRole('ADMIN')")
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
                .sorted(java.util.Comparator.comparing(
                        org.springframework.batch.core.JobExecution::getCreateTime,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())).reversed())
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> execute(
            @PathVariable String jobName,
            @RequestParam(required = false) Integer shopNo) {
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
