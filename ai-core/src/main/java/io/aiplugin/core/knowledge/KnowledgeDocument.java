package io.aiplugin.core.knowledge;

import java.util.List;

/**
 * 도메인 지식 문서(용어집, 업무 규칙, 코드표 등).
 *
 * @param alwaysInclude true 면 질의와 관계없이 항상 컨텍스트에 포함 (파일명이 "instructions" 로 시작하는 문서)
 */
public record KnowledgeDocument(String title, String content, List<String> tags, boolean alwaysInclude) {

    public KnowledgeDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
