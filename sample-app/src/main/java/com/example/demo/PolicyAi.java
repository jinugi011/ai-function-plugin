package com.example.demo;

import io.aiplugin.spring.annotation.AiPrompt;
import io.aiplugin.spring.annotation.AiService;

import java.util.Map;

/** RAG 가 켜져 있으면 벡터 DB 에서 관련 정책 문서를 찾아 자동으로 프롬프트에 넣습니다. */
@AiService(system = "당신은 쇼핑몰 고객센터 상담원입니다. 제공된 문서에 근거해서만 답하세요.")
public interface PolicyAi {

    @AiPrompt(value = "고객 질문: {question}\nanswer(답변), source(근거 문서), confident(yes/no) 키로 답해줘",
            useKnowledge = false)
    Map<String, String> answer(String question);
}
