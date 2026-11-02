package io.aiplugin.spring.proxy;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import io.aiplugin.core.AiClient;
import io.aiplugin.core.AiResult;
import io.aiplugin.core.PromptCall;
import io.aiplugin.core.ResponseFormat;
import io.aiplugin.spring.annotation.AiPrompt;
import io.aiplugin.spring.annotation.AiService;
import io.aiplugin.spring.annotation.PromptFormat;
import io.aiplugin.spring.annotation.PromptVar;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** {@code @AiPrompt} 메서드 호출을 AI 요청으로 변환하고 응답을 반환 타입으로 매핑합니다. */
class AiServiceInvocationHandler implements InvocationHandler {

    private final Class<?> serviceInterface;
    private final AiClient client;
    private final AiService service;
    private final Map<Method, MethodPlan> plans = new ConcurrentHashMap<>();

    AiServiceInvocationHandler(Class<?> serviceInterface, AiClient client) {
        this.serviceInterface = serviceInterface;
        this.client = client;
        this.service = serviceInterface.getAnnotation(AiService.class);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "AiService proxy for " + serviceInterface.getName();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
        if (method.isDefault()) {
            return MethodHandles.privateLookupIn(serviceInterface, MethodHandles.lookup())
                    .unreflectSpecial(method, serviceInterface)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        }
        MethodPlan plan = plans.computeIfAbsent(method, this::plan);
        return plan.execute(args == null ? new Object[0] : args);
    }

    private MethodPlan plan(Method method) {
        AiPrompt prompt = method.getAnnotation(AiPrompt.class);
        if (prompt == null) {
            throw new IllegalStateException(method + " must be annotated with @AiPrompt");
        }
        JavaType returnType = client.parser().mapper().getTypeFactory().constructType(method.getGenericReturnType());
        ResponseFormat format = resolveFormat(prompt.format(), returnType);

        String system = join(service.system(), prompt.system());
        if (format == ResponseFormat.JSON && !returnType.isTypeOrSubTypeOf(JsonNode.class)
                && !returnType.isTypeOrSubTypeOf(String.class) && !returnType.isTypeOrSubTypeOf(AiResult.class)) {
            system = join(system, "The JSON must have this structure:\n"
                    + TypeDescriber.describe(client.parser().mapper(), returnType));
        }

        Parameter[] params = method.getParameters();
        String[] names = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            PromptVar var = params[i].getAnnotation(PromptVar.class);
            names[i] = var != null ? var.value() : params[i].getName();
        }
        return new MethodPlan(prompt, firstNonEmpty(prompt.provider(), service.provider()),
                firstNonEmpty(prompt.model(), service.model()), system,
                firstNonEmpty(prompt.knowledgeTag(), service.knowledgeTag()), format, returnType, names, method);
    }

    private static ResponseFormat resolveFormat(PromptFormat declared, JavaType type) {
        if (declared != PromptFormat.AUTO) {
            return ResponseFormat.valueOf(declared.name());
        }
        if (type.isTypeOrSubTypeOf(String.class) || type.isTypeOrSubTypeOf(AiResult.class)) {
            return ResponseFormat.TEXT;
        }
        if (type.isMapLikeType()) {
            return ResponseFormat.KEY_VALUE;
        }
        return ResponseFormat.JSON;
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null || b.isBlank() ? null : b;
        }
        return b == null || b.isBlank() ? a : a + "\n\n" + b;
    }

    private final class MethodPlan {
        private final AiPrompt prompt;
        private final String provider;
        private final String model;
        private final String system;
        private final String knowledgeTag;
        private final ResponseFormat format;
        private final JavaType returnType;
        private final String[] paramNames;
        private final Method method;

        MethodPlan(AiPrompt prompt, String provider, String model, String system, String knowledgeTag,
                   ResponseFormat format, JavaType returnType, String[] paramNames, Method method) {
            this.prompt = prompt;
            this.provider = provider;
            this.model = model;
            this.system = system;
            this.knowledgeTag = knowledgeTag;
            this.format = format;
            this.returnType = returnType;
            this.paramNames = paramNames;
            this.method = method;
        }

        Object execute(Object[] args) {
            Map<String, Object> vars = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                vars.put(paramNames[i], args[i]);
            }
            String template = prompt.value();
            if (template.isBlank()) {
                template = firstStringArg(args);
            }
            PromptCall call = client.prompt(template).vars(vars)
                    .provider(provider)
                    .model(model)
                    .system(system)
                    .knowledgeTag(knowledgeTag)
                    .useKnowledge(prompt.useKnowledge());
            if (prompt.maxTokens() > 0) {
                call.maxTokens(prompt.maxTokens());
            }

            AiResult result = call.call(format);
            if (returnType.isTypeOrSubTypeOf(AiResult.class)) {
                return result;
            }
            if (returnType.isTypeOrSubTypeOf(String.class)) {
                return result.text();
            }
            if (returnType.isTypeOrSubTypeOf(JsonNode.class)) {
                return result.json();
            }
            if (format == ResponseFormat.KEY_VALUE && returnType.isMapLikeType()) {
                return result.keyValue();
            }
            return result.as(returnType);
        }

        private String firstStringArg(Object[] args) {
            for (Object a : args) {
                if (a instanceof CharSequence s) {
                    return s.toString();
                }
            }
            throw new IllegalStateException(method + ": @AiPrompt value is empty and no String argument was given");
        }
    }
}
