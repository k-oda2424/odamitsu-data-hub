package jp.co.oda32;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * バッチアプリケーションのエントリポイント
 * 指定されたジョブのみを明示的に実行します
 */
@Profile("batch")
@SpringBootApplication
@EnableTransactionManagement
public class BatchApplication {

    public static void main(String[] args) {
        String jobName = null;

        for (String arg : args) {
            if (arg.startsWith("--spring.batch.job.name=")) {
                jobName = arg.substring("--spring.batch.job.name=".length());
            }
        }

        if (jobName == null || jobName.isEmpty()) {
            System.out.println("ジョブ名が指定されていません。--spring.batch.job.name=<ジョブ名> を指定してください。");
            System.exit(1);
            return;
        }

        System.out.println("指定されたジョブ [" + jobName + "] のみを実行します...");

        SpringApplication app = new SpringApplication(BatchApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        try {
            JobLauncher jobLauncher = context.getBean(JobLauncher.class);
            String beanName = jobName + "Job";

            if (!context.containsBean(beanName)) {
                System.err.println("エラー: 指定されたジョブ [" + jobName + "] に対応するBean [" + beanName + "] が見つかりません");
                System.exit(1);
                return;
            }

            Job job = (Job) context.getBean(beanName);
            System.out.println("ジョブを実行します: " + job.getName());

            JobParametersBuilder parametersBuilder = new JobParametersBuilder();
            parametersBuilder.addLong("time", System.currentTimeMillis());

            for (String arg : args) {
                if (arg.startsWith("--") && !arg.startsWith("--spring.batch.") && !arg.startsWith("--application.")) {
                    String paramStr = arg.substring(2);
                    if (paramStr.contains("=")) {
                        String key = paramStr.substring(0, paramStr.indexOf('='));
                        String value = paramStr.substring(paramStr.indexOf('=') + 1);
                        parametersBuilder.addString(key, value);
                    }
                } else if (!arg.startsWith("--") && arg.contains("=")) {
                    String key = arg.substring(0, arg.indexOf('='));
                    String value = arg.substring(arg.indexOf('=') + 1);
                    parametersBuilder.addString(key, value);
                }
            }

            System.out.println("ジョブ [" + jobName + "] を実行開始します...");
            jobLauncher.run(job, parametersBuilder.toJobParameters());
            System.out.println("ジョブ [" + jobName + "] の実行が完了しました。");
        } catch (Exception e) {
            System.err.println("ジョブの実行中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        } finally {
            int exitCode = SpringApplication.exit(context, () -> 0);
            System.exit(exitCode);
        }
    }
}
