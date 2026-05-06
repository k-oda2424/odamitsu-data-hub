package jp.co.oda32.audit;

import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * G3-M8 (2026-05-06): {@link BatchAuditJobListener} を全 {@link Job} Bean に自動配線する
 * {@link BeanPostProcessor}。
 *
 * <p>各 *JobConfig.java を個別編集する代わりに、{@link AbstractJob#registerJobExecutionListener}
 * を BeanPostProcessor 経由で呼び出して横断適用する。これにより:
 * <ul>
 *   <li>新規 Job 追加時に audit 配線忘れが起きない</li>
 *   <li>既存 19 Job Config を変更不要 (= レビュー差分極小)</li>
 *   <li>{@code JobBuilder.listener(...)} で各 Config が個別に登録している
 *       {@code JobStartEndListener} 等は維持される (本 listener は<b>追加</b>される)</li>
 * </ul>
 *
 * <p>{@code JobBuilder.build()} は {@code SimpleJob}/{@code FlowJob} を返し、いずれも
 * {@link AbstractJob} を継承しているため安全に cast できる。
 * {@link AbstractJob} 以外の {@link Job} 実装が登録された場合は WARN ログのみ
 * (= silently skip しない、運用で気付ける)。
 *
 * <p>{@link ObjectProvider} で listener を遅延注入することで、テスト等で listener を
 * 登録しない構成でも循環 / 起動失敗を起こさない。
 *
 * @see BatchAuditJobListener
 * @since 2026-05-06 (G3-M8)
 */
@Component
@Log4j2
public class BatchAuditListenerAutoRegistrar implements BeanPostProcessor {

    private final ObjectProvider<BatchAuditJobListener> listenerProvider;

    public BatchAuditListenerAutoRegistrar(ObjectProvider<BatchAuditJobListener> listenerProvider) {
        this.listenerProvider = listenerProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof Job job)) {
            return bean;
        }
        BatchAuditJobListener listener = listenerProvider.getIfAvailable();
        if (listener == null) {
            // バッチ audit listener Bean が登録されていない構成 (= 一部テスト) は noop
            return bean;
        }
        if (job instanceof AbstractJob abstractJob) {
            abstractJob.registerJobExecutionListener(listener);
            log.debug("[batch-audit] BatchAuditJobListener を Job '{}' に自動登録した", job.getName());
        } else {
            log.warn("[batch-audit] Job '{}' は AbstractJob ではないため audit listener を登録できない (type={})",
                    job.getName(), job.getClass().getName());
        }
        return bean;
    }
}
