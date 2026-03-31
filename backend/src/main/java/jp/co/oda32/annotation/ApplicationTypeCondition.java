package jp.co.oda32.annotation;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;

public class ApplicationTypeCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        MultiValueMap<String, Object> attrs = metadata.getAllAnnotationAttributes(ApplicationType.class.getName());
        if (attrs != null) {
            for (Object value : attrs.get("value")) {
                Environment env = context.getEnvironment();
                Object prop = env.getProperty("application.type");
                String[] profiles = (String[]) value;
                if (Arrays.asList(profiles).contains(prop)) {
                    return true;
                }
            }
        }
        return false;
    }
}
