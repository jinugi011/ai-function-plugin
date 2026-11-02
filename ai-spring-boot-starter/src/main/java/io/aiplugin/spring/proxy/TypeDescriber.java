package io.aiplugin.spring.proxy;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import java.time.temporal.Temporal;
import java.util.Date;
import java.util.UUID;

/** 반환 타입을 {"name": string, "items": [{...}]} 형태의 간단한 구조 설명으로 바꿔 모델에게 알려줍니다. */
final class TypeDescriber {

    private static final int MAX_DEPTH = 4;

    private TypeDescriber() {
    }

    static String describe(ObjectMapper mapper, JavaType type) {
        StringBuilder sb = new StringBuilder();
        append(mapper, type, sb, 0);
        return sb.toString();
    }

    private static void append(ObjectMapper mapper, JavaType type, StringBuilder sb, int depth) {
        Class<?> raw = type.getRawClass();
        if (raw == String.class || raw == Character.class || raw == char.class || raw == UUID.class
                || Temporal.class.isAssignableFrom(raw) || Date.class.isAssignableFrom(raw)) {
            sb.append("string");
        } else if (raw == boolean.class || raw == Boolean.class) {
            sb.append("boolean");
        } else if (raw.isPrimitive() || Number.class.isAssignableFrom(raw)) {
            sb.append("number");
        } else if (raw.isEnum()) {
            sb.append("one of ");
            Object[] constants = raw.getEnumConstants();
            for (int i = 0; i < constants.length; i++) {
                sb.append(i == 0 ? "" : "|").append('"').append(constants[i]).append('"');
            }
        } else if (type.isCollectionLikeType() || type.isArrayType()) {
            sb.append('[');
            append(mapper, type.getContentType(), sb, depth + 1);
            sb.append(']');
        } else if (type.isMapLikeType()) {
            sb.append("{string: ");
            append(mapper, type.getContentType(), sb, depth + 1);
            sb.append('}');
        } else if (depth >= MAX_DEPTH || raw == Object.class) {
            sb.append("any");
        } else {
            BeanDescription desc = mapper.getDeserializationConfig().introspect(type);
            sb.append('{');
            boolean first = true;
            for (BeanPropertyDefinition prop : desc.findProperties()) {
                if (!prop.couldDeserialize()) {
                    continue;
                }
                sb.append(first ? "" : ", ").append('"').append(prop.getName()).append("\": ");
                append(mapper, prop.getPrimaryType(), sb, depth + 1);
                first = false;
            }
            sb.append('}');
        }
    }
}
