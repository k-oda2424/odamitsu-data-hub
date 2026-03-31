package jp.co.oda32.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

@Slf4j
public class JobStartEndListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("バッチジョブ開始: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("バッチジョブ終了: {} - ステータス: {}", jobExecution.getJobInstance().getJobName(), jobExecution.getStatus());
    }
}
