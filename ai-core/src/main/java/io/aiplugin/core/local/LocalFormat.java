package io.aiplugin.core.local;

import io.aiplugin.core.ResponseFormat;

/** 로컬 모델용 형식 지시문 (작은 모델도 따르도록 짧고 명확하게). */
final class LocalFormat {

    private LocalFormat() {
    }

    static String mergeSystem(String system, ResponseFormat format) {
        String instruction = switch (format) {
            case JSON -> "Answer with valid JSON only. No markdown, no explanation.";
            case KEY_VALUE -> "Answer with one flat JSON object whose values are all strings. No markdown, no explanation.";
            case TEXT -> null;
        };
        if (instruction == null) {
            return system;
        }
        return system == null || system.isBlank() ? instruction : system + "\n\n" + instruction;
    }
}
