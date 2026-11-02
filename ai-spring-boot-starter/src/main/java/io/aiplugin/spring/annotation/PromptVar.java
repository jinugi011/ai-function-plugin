package io.aiplugin.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 프롬프트 템플릿 변수 이름을 지정합니다. 없으면 파라미터 이름(-parameters 컴파일 옵션 필요)을 사용합니다. */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PromptVar {
    String value();
}
