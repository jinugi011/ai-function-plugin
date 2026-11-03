package io.aiplugin.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI 를 호출하는 메서드에 붙입니다.
 *
 * <ul>
 *   <li>{@code value} 의 {@code {name}} 자리표시자는 같은 이름의 메서드 파라미터 값으로 치환됩니다
 *       ({@link PromptVar} 로 이름 지정 가능).</li>
 *   <li>{@code value} 가 비어 있으면 첫 번째 String 파라미터를 프롬프트 그대로 사용합니다.</li>
 *   <li>응답 형식은 반환 타입으로 결정됩니다: String → 텍스트, Map → key-value, JsonNode → JSON,
 *       그 외 클래스/record/List → JSON 을 해당 타입으로 매핑.</li>
 * </ul>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiPrompt {

    /** 프롬프트 템플릿. */
    String value() default "";

    /** 응답 형식. AUTO 면 반환 타입으로 결정. */
    PromptFormat format() default PromptFormat.AUTO;

    String provider() default "";

    String model() default "";

    String system() default "";

    /** 0 이면 프로바이더 기본값. */
    int maxTokens() default 0;

    /** 지식베이스(사전 학습 데이터)를 컨텍스트로 사용할지 여부. */
    boolean useKnowledge() default true;

    String knowledgeTag() default "";

    /** RAG(벡터 DB 문서 검색) 결과를 컨텍스트로 사용할지 여부. ai.rag.enabled=true 일 때만 동작. */
    boolean useRag() default true;
}
