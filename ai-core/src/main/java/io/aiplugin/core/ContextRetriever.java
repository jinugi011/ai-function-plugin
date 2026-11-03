package io.aiplugin.core;

/** 질의와 관련된 외부 문서(RAG)를 찾아 프롬프트 컨텍스트로 돌려줍니다. */
public interface ContextRetriever {

    /** 관련 문서가 없으면 빈 문자열. */
    String retrieveContext(String query);
}
