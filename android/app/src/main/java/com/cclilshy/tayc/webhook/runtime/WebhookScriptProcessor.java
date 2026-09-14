package com.cclilshy.tayc.webhook.runtime;

import com.cclilshy.tayc.webhook.domain.WebhookChannel;
import com.cclilshy.tayc.webhook.domain.WebhookRequest;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WebhookScriptProcessor {
    public static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final long MAX_EXECUTION_NANOS = 500_000_000L;
    private static final long MAX_INSTRUCTIONS = 1_000_000L;
    private static final Object BUDGET_KEY = new Object();
    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override
        protected Context makeContext() {
            Context context = super.makeContext();
            context.setInterpretedMode(true);
            context.setLanguageVersion(Context.VERSION_ES6);
            context.setClassShutter(name -> false);
            context.setMaximumInterpreterStackDepth(128);
            context.setInstructionObserverThreshold(1000);
            context.putThreadLocal(BUDGET_KEY, new Budget());
            return context;
        }

        @Override
        protected void observeInstructionCount(Context context, int count) {
            Budget budget = (Budget) context.getThreadLocal(BUDGET_KEY);
            budget.instructions += count;
            if (Thread.currentThread().isInterrupted()
                    || budget.instructions > MAX_INSTRUCTIONS
                    || System.nanoTime() - budget.started > MAX_EXECUTION_NANOS) {
                throw new ScriptLimitError();
            }
        }
    };

    private WebhookScriptProcessor() {
    }

    public static void validate(String source) {
        if (source == null) {
            return;
        }
        checkSource(source);
        if (source.trim().isEmpty()) {
            return;
        }
        try {
            FACTORY.call(context -> {
                context.compileString(source, "webhook.js", 1, null);
                return null;
            });
        } catch (RhinoException err) {
            throw new IllegalArgumentException("JavaScript syntax error at line " + err.lineNumber(), err);
        } catch (ScriptLimitError err) {
            throw new IllegalArgumentException(err.getMessage(), err);
        }
    }

    public static WebhookRequest process(String source, WebhookRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("webhook request is required");
        }
        if (source == null) {
            return request;
        }
        checkSource(source);
        if (source.trim().isEmpty()) {
            return request;
        }
        checkJsonSize(request.getEventJson());
        try {
            return FACTORY.call(context -> evaluate(context, source, request));
        } catch (RhinoException err) {
            throw new IllegalArgumentException("JavaScript failed at line " + err.lineNumber(), err);
        } catch (ScriptLimitError err) {
            throw new IllegalArgumentException(err.getMessage(), err);
        }
    }

    private static WebhookRequest evaluate(Context context, String source, WebhookRequest request) {
        ScriptableObject scope = context.initSafeStandardObjects();
        Scriptable json = (Scriptable) ScriptableObject.getProperty(scope, "JSON");
        Function parseJson = (Function) ScriptableObject.getProperty(json, "parse");
        Object event = parseJson.call(context, scope, json, new Object[]{request.getEventJson()});
        Scriptable channel = context.newObject(scope);
        WebhookRequest.Channel original = request.getChannel();
        ScriptableObject.putProperty(channel, "name", original.getName());
        ScriptableObject.putProperty(channel, "url", original.getUrl());
        ScriptableObject.putProperty(channel, "proxyUrl", original.getProxyUrl());
        Scriptable headers = context.newObject(scope);
        for (Map.Entry<String, String> header : original.getHeaders().entrySet()) {
            ScriptableObject.putProperty(headers, header.getKey(), header.getValue());
        }
        ScriptableObject.putProperty(channel, "headers", headers);
        Scriptable input = context.newObject(scope);
        ScriptableObject.putProperty(input, "event", event);
        ScriptableObject.putProperty(input, "channel", channel);
        // Bound the complete input snapshot, including channel URLs and headers.
        stringify(context, scope, input);

        context.evaluateString(scope, source, "webhook.js", 1, null);
        Object handler = ScriptableObject.getProperty(scope, "onEvent");
        if (!(handler instanceof Function)) {
            throw new IllegalArgumentException("JavaScript must define onEvent(event, channel)");
        }
        Object result = ((Function) handler).call(context, scope, scope, new Object[]{event, channel});
        if (!(result instanceof NativeObject)) {
            throw new IllegalArgumentException("onEvent must return {event, channel}");
        }
        String serialized = stringify(context, scope, result);
        NativeObject output = object(parseJson.call(context, scope, json, new Object[]{serialized}), "result");
        Object outputEvent = required(output, "event");
        NativeObject outputChannel = object(required(output, "channel"), "channel");
        NativeObject outputHeaders = object(required(outputChannel, "headers"), "channel.headers");
        Map<String, String> resultHeaders = new LinkedHashMap<>();
        for (Object id : outputHeaders.getIds()) {
            String name = id.toString();
            Object value = id instanceof Number
                    ? outputHeaders.get(((Number) id).intValue(), outputHeaders)
                    : outputHeaders.get(name, outputHeaders);
            resultHeaders.put(name, string(value, "header value"));
        }
        Object proxyUrl = outputChannel.has("proxyUrl", outputChannel)
                ? outputChannel.get("proxyUrl", outputChannel)
                : Scriptable.NOT_FOUND;
        WebhookRequest.Channel destination = new WebhookRequest.Channel(
                string(required(outputChannel, "name"), "channel.name"),
                string(required(outputChannel, "url"), "channel.url"),
                proxyUrl == Scriptable.NOT_FOUND || proxyUrl == null ? "" : string(proxyUrl, "channel.proxyUrl"),
                resultHeaders);
        return new WebhookRequest(stringify(context, scope, outputEvent), destination);
    }

    private static Object required(Scriptable object, String name) {
        Object value = object.has(name, object) ? object.get(name, object) : Scriptable.NOT_FOUND;
        if (value == Scriptable.NOT_FOUND || Undefined.isUndefined(value)) {
            throw new IllegalArgumentException("onEvent result is missing " + name);
        }
        return value;
    }

    private static NativeObject object(Object value, String field) {
        if (!(value instanceof NativeObject)) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        return (NativeObject) value;
    }

    private static String string(Object value, String field) {
        if (!(value instanceof CharSequence)) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.toString();
    }

    private static String stringify(Context context, Scriptable scope, Object value) {
        Object result = NativeJSON.stringify(context, scope, value, null, null);
        if (!(result instanceof CharSequence)) {
            throw new IllegalArgumentException("onEvent result must be JSON serializable");
        }
        String json = result.toString();
        checkJsonSize(json);
        return json;
    }

    private static void checkSource(String source) {
        if (source.length() > WebhookChannel.MAX_SCRIPT_LENGTH) {
            throw new IllegalArgumentException("webhook script is too long");
        }
    }

    private static void checkJsonSize(String json) {
        if (json.length() > MAX_JSON_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("webhook script JSON exceeds 1 MiB");
        }
    }

    private static final class Budget {
        final long started = System.nanoTime();
        long instructions;
    }

    // ponytail: instruction limits are not a heap quota; use process isolation for untrusted third-party scripts.
    private static final class ScriptLimitError extends Error {
        ScriptLimitError() {
            super("JavaScript execution limit exceeded");
        }
    }
}
