package io.aiplugin.core.rag;

/** 검색 결과. score 는 코사인 유사도 (1 에 가까울수록 유사). */
public record SearchResult(Chunk chunk, double score) {
}
