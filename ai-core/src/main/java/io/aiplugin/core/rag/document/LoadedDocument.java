package io.aiplugin.core.rag.document;

import java.util.List;

/**
 * 읽어들인 문서. PDF 는 페이지별로, 텍스트는 한 덩어리로 들어갑니다.
 *
 * @param source 문서 식별자
 * @param pages  페이지별 텍스트 (텍스트 파일은 1개)
 * @param hash   원본 바이트의 SHA-256 (변경 감지용)
 */
public record LoadedDocument(String source, List<String> pages, String hash) {
}
