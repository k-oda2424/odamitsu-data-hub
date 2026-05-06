package jp.co.oda32;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test configuration class for Spring Boot tests.
 * Excludes JPA/Batch auto-configuration and limits component scanning to
 * config and API packages to avoid loading services that depend on JPA repositories.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        BatchAutoConfiguration.class
})
@ComponentScan(
        basePackages = "jp.co.oda32",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        "jp\\.co\\.oda32\\.domain\\.service\\..*",
                        "jp\\.co\\.oda32\\.domain\\.repository\\..*",
                        "jp\\.co\\.oda32\\.batch\\..*",
                        "jp\\.co\\.oda32\\.api\\.(?!auth).*",
                        "jp\\.co\\.oda32\\.aop\\..*",
                        "jp\\.co\\.oda32\\.annotation\\..*",
                        "jp\\.co\\.oda32\\.audit\\..*"
                }
        )
)
public class TestApplication {
}
