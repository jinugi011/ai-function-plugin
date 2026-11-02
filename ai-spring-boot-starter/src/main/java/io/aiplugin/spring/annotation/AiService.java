package io.aiplugin.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인터페이스를 AI 서비스로 선언합니다. 구현체는 런타임 프록시로 생성되어 Spring 빈으로 등록됩니다.
 * 여기 지정한 값은 각 메서드의 {@link AiPrompt} 기본값이 됩니다.
 *
 * <pre>{@code
 * @AiService(provider = "claude")
 * public interface ProductAi {
 *     @AiPrompt("다음 상품 설명에서 상품 정보를 추출해: {description}")
 *     ProductInfo extract(String description);
 * }
 * }</pre>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiService {

    /** 사용할 프로바이더 이름 (openai, claude, abacus). 비우면 ai.default-provider. */
    String provider() default "";

    /** 모델 ID. 비우면 프로바이더 기본 모델. */
    String model() default "";

    /** 모든 메서드에 공통으로 붙는 시스템 지시문. */
    String system() default "";

    /** 지식베이스에서 이 태그가 붙은 항목만 사용. */
    String knowledgeTag() default "";
}
