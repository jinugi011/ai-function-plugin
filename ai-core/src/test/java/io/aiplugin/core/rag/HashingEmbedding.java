package io.aiplugin.core.rag;

import io.aiplugin.core.rag.embedding.EmbeddingModel;

import java.util.List;
import java.util.Locale;

/** 테스트용 결정적 임베딩: 문자 bigram 을 해싱한 벡터. 같은 단어가 많을수록 유사도가 높습니다. */
public class HashingEmbedding implements EmbeddingModel {

    public static final int DIM = 256;
    public int calls;

    @Override
    public String id() {
        return "test/hashing";
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        calls++;
        return texts.stream().map(HashingEmbedding::vector).toList();
    }

    static float[] vector(String text) {
        float[] v = new float[DIM];
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
        for (int i = 0; i + 2 <= s.length(); i++) {
            String g = s.substring(i, i + 2);
            if (!g.isBlank()) {
                v[Math.floorMod(g.hashCode(), DIM)] += 1;
            }
        }
        return v;
    }
}
