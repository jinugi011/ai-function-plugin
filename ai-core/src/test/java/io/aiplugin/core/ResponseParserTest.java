package io.aiplugin.core;

import io.aiplugin.core.parse.ResponseParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseParserTest {

    private final ResponseParser parser = new ResponseParser();

    record Person(String name, int age) {
    }

    @Test
    void extractsJsonFromCodeFenceAndChatter() {
        String raw = "결과입니다:\n```json\n{\"name\": \"홍길동\", \"age\": 30}\n```\n감사합니다.";
        assertEquals("홍길동", parser.toJson(raw).get("name").asText());
        assertEquals(new Person("홍길동", 30), parser.toObject(raw, Person.class));
    }

    @Test
    void flattensNestedJsonToKeyValue() {
        Map<String, String> kv = parser.toKeyValue("{\"a\": {\"b\": 1}, \"list\": [\"x\", \"y\"], \"s\": \"{not}\"}");
        assertEquals("1", kv.get("a.b"));
        assertEquals("y", kv.get("list[1]"));
        assertEquals("{not}", kv.get("s"));
    }

    @Test
    void parsesPlainKeyValueLines() {
        Map<String, String> kv = parser.toKeyValue("name: 홍길동\n- city = 서울\n\"age\": \"30\"");
        assertEquals(Map.of("name", "홍길동", "city", "서울", "age", "30"), kv);
    }

    @Test
    void rendersTemplateVariables() {
        assertEquals("hi Kim, {unknown}",
                PromptTemplate.render("hi {name}, {unknown}", Map.of("name", "Kim")));
    }
}
