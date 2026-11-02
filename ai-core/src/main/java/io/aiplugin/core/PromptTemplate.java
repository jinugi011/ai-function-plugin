package io.aiplugin.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "{name}" 형태의 자리표시자를 값으로 치환하는 단순 템플릿. 값이 없는 자리표시자는 그대로 둡니다. */
public final class PromptTemplate {

    private static final Pattern VAR = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_.]*)}");

    private PromptTemplate() {
    }

    public static String render(String template, Map<String, ?> vars) {
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = vars.containsKey(key) ? String.valueOf(vars.get(key)) : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
