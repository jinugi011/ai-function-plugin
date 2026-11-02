package io.aiplugin.spring.annotation;

import io.aiplugin.spring.proxy.AiServiceRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link AiService} 인터페이스를 스캔할 패키지를 지정합니다.
 * Spring Boot 에서는 생략해도 {@code @SpringBootApplication} 패키지 아래를 자동 스캔합니다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AiServiceRegistrar.class)
public @interface EnableAiServices {

    String[] basePackages() default {};

    Class<?>[] basePackageClasses() default {};
}
