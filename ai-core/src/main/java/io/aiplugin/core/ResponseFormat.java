package io.aiplugin.core;

/** AI 응답을 어떤 형태로 받을지 지정합니다. */
public enum ResponseFormat {
    /** 가공하지 않은 텍스트 */
    TEXT,
    /** JSON 객체/배열 */
    JSON,
    /** 평면적인 key-value 쌍 (Map&lt;String, String&gt;) */
    KEY_VALUE
}
