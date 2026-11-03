package io.aiplugin.core.rag.embedding;

import java.util.List;

/** 텍스트를 벡터로 변환합니다. 문서 색인과 검색에는 반드시 같은 모델을 써야 합니다. */
public interface EmbeddingModel {

    /** 저장소에 기록되는 모델 식별자 (예: "openai/text-embedding-3-small"). */
    String id();

    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }
}
