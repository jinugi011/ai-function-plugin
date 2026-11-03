package io.aiplugin.core.rag.store;

import io.aiplugin.core.rag.Chunk;
import io.aiplugin.core.rag.SearchResult;

import java.util.List;
import java.util.Map;

/** 벡터 DB 추상화. 구현: {@link FileVectorStore}, {@link QdrantVectorStore}, {@link PgVectorStore}. */
public interface VectorStore {

    /** 같은 id 가 있으면 덮어씁니다. vectors 는 chunks 와 같은 순서. */
    void upsert(List<Chunk> chunks, List<float[]> vectors);

    List<SearchResult> search(float[] query, int topK, double minScore);

    /** 해당 문서의 모든 조각을 삭제합니다. */
    void deleteSource(String source);

    /** 같은 내용(hash)의 문서가 이미 색인되어 있는지. 변경되지 않은 파일의 재색인을 건너뛰는 데 사용합니다. */
    boolean containsSource(String source, String hash);

    long count();

    /** 문서별 조각 수. 지원하지 않는 저장소는 빈 Map. */
    default Map<String, Long> sources() {
        return Map.of();
    }

    /** 변경 내용을 영구 저장 (파일 저장소용). */
    default void flush() {
    }
}
