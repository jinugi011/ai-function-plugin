package io.aiplugin.core.provider;

import io.aiplugin.core.ResponseFormat;

/** 응답 형식별로 모델에게 붙이는 공통 지시문. */
final class FormatInstructions {

    private FormatInstructions() {
    }

    static String forFormat(ResponseFormat format) {
        return switch (format) {
            case JSON -> "Respond with a single valid JSON value only. Do not wrap it in markdown code fences "
                    + "and do not add any explanation before or after it.";
            case KEY_VALUE -> "Respond with a single flat JSON object whose keys and values are strings only "
                    + "(no nested objects or arrays). Do not wrap it in markdown code fences and do not add "
                    + "any explanation.";
            case TEXT -> null;
        };
    }

    static String mergeSystem(String system, ResponseFormat format) {
        String instruction = forFormat(format);
        if (instruction == null) {
            return system;
        }
        return system == null || system.isBlank() ? instruction : system + "\n\n" + instruction;
    }
}
