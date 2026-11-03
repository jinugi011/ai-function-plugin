package io.aiplugin.core.rag;

import java.util.Map;

/**
 * 벡터 DB 에 저장되는 문서 조각.
 *
 * @param id       고유 ID (source + index 기반 UUID)
 * @param source   원본 문서 식별자 (파일 경로 등)
 * @param index    문서 내 조각 순번
 * @param content  본문
 * @param metadata 추가 정보 (page, hash, title 등)
 */
public record Chunk(String id, String source, int index, String content, Map<String, String> metadata) {

    public Chunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
