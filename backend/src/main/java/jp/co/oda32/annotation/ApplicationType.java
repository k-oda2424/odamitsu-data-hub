package jp.co.oda32.annotation;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ApplicationTypeCondition.class)
public @interface ApplicationType {
    String[] value();
}
