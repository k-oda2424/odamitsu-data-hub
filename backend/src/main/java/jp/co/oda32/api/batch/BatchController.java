package jp.co.oda32.api.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {

    private static final Set<String> ALLOWED_JOBS = Set.of(
            "accountsPayableAggregation",
            "accountsPayableVerification",
            "accountsPayableSummary",
            "accountsReceivableSummary",
            "purchaseJournalIntegration",
            "salesJournalIntegration",
            "purchaseFileImport",
            "goodsFileImport",
            "smileOrderFileImport",
            "smilePaymentImport"
    );

    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @PostMapping("/execute/{jobName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> execute(@PathVariable String jobName) {
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
            jobLauncher.run(job, params.toJobParameters());
            return ResponseEntity.ok(Map.of("message", "ジョブを実行しました: " + jobName));
        } catch (Exception e) {
            log.error("バッチジョブ実行エラー: jobName={}", jobName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "ジョブ実行中にエラーが発生しました"));
        }
    }
}
