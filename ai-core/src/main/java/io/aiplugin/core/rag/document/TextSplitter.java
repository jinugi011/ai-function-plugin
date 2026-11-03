package io.aiplugin.core.rag.document;

import java.util.ArrayList;
import java.util.List;

/**
 * 텍스트를 임베딩하기 좋은 크기로 자릅니다. 문단 → 문장 → 글자 순으로 경계를 찾고, 앞 조각과 일부를 겹쳐
 * 문맥이 끊기지 않게 합니다.
 */
public final class TextSplitter {

    private final int chunkSize;
    private final int overlap;

    /**
     * @param chunkSize 조각 최대 글자 수 (한국어 기준 500~1000 권장)
     * @param overlap   앞 조각과 겹치는 글자 수
     */
    public TextSplitter(int chunkSize, int overlap) {
        if (chunkSize < 50) {
            throw new IllegalArgumentException("chunkSize must be >= 50");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be in [0, chunkSize)");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<String> split(String text) {
        String normalized = text.replace("\r\n", "\n").replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\n{3,}", "\n\n").strip();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            if (end < normalized.length()) {
                end = findBreak(normalized, start, end);
            }
            String chunk = normalized.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            int next = end - overlap;
            // 겹침 시작을 단어 경계로 맞춤
            while (next > start && next < end && !Character.isWhitespace(normalized.charAt(next - 1))) {
                next++;
            }
            start = next <= start ? end : next;
        }
        return chunks;
    }

    /** [start, end) 안에서 뒤쪽 절반 이후의 가장 좋은 경계 위치. */
    private int findBreak(String s, int start, int end) {
        int min = start + chunkSize / 2;
        int para = s.lastIndexOf("\n\n", end);
        if (para >= min) {
            return para;
        }
        for (int i = end - 1; i >= min; i--) {
            char c = s.charAt(i);
            if ((c == '.' || c == '?' || c == '!' || c == '。' || c == '\n') && i + 1 < s.length()
                    && Character.isWhitespace(s.charAt(i + 1))) {
                return i + 1;
            }
        }
        int space = s.lastIndexOf(' ', end);
        return space >= min ? space : end;
    }
}
