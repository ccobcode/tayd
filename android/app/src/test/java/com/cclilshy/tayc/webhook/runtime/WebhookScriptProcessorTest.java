package com.cclilshy.tayc.webhook.runtime;

import com.cclilshy.tayc.webhook.data.WebhookChannelStore;
import com.cclilshy.tayc.webhook.domain.WebhookChannel;
import com.cclilshy.tayc.webhook.domain.WebhookRequest;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WebhookScriptProcessorTest {
    private static final String EVENT = "{\"type\":\"sms\",\"data\":{\"text\":\"original\"}}";
    private static final String IDENTITY = "function onEvent(event, channel) { return {event, channel}; }";

    private static WebhookRequest request() {
        return WebhookRequest.from(new WebhookChannel("ops", "https://original.example/events", "", ""), EVENT);
    }

    @Test
    public void emptyScriptPreservesExactRequestAndJsonText() {
        WebhookRequest request = new WebhookRequest("  " + EVENT + "\n", request().getChannel());
        assertSame(request, WebhookScriptProcessor.process("", request));
        assertSame(request, WebhookScriptProcessor.process(" \n\t", request));
        assertSame(request, WebhookScriptProcessor.process(null, request));
    }

    @Test
    public void changesBodyAndChannelOnlyForThisDelivery() {
        String source = "function onEvent(event, channel) {"
                + "event.data.text = 'changed';"
                + "channel.url = 'https://modified.example/events';"
                + "channel.proxyUrl = 'http://127.0.0.1:8081';"
                + "channel.headers = {'Content-Type':'application/custom+json','X-Event':'sms'};"
                + "return {event:{message:event.data.text},channel}; }";
        WebhookChannel saved = new WebhookChannel("ops", "https://original.example/events", "", source);
        String stored = WebhookChannelStore.serialize(Collections.singletonList(saved));
        WebhookRequest original = WebhookRequest.from(saved, EVENT);
        WebhookRequest result = WebhookScriptProcessor.process(saved.getScript(), original);

        assertEquals("{\"message\":\"changed\"}", result.getEventJson());
        assertEquals("https://modified.example/events", result.getChannel().getUrl());
        assertEquals("http://127.0.0.1:8081", result.getChannel().getProxyUrl());
        assertEquals("sms", result.getChannel().getHeaders().get("X-Event"));
        assertEquals("application/custom+json", result.getChannel().getHeaders().get("Content-Type"));
        assertFalse(result.getChannel().getHeaders().containsKey("Accept"));
        assertEquals(EVENT, original.getEventJson());
        assertEquals("https://original.example/events", original.getChannel().getUrl());
        assertEquals("application/json", original.getChannel().getHeaders().get("Accept"));
        assertEquals(stored, WebhookChannelStore.serialize(Collections.singletonList(saved)));
        assertThrows(UnsupportedOperationException.class, () -> result.getChannel().getHeaders().put("X-New", "value"));
    }

    @Test
    public void eventNullAndArraysAreJsonValuesNotMissingBodies() {
        assertEquals("null", WebhookScriptProcessor.process(
                "function onEvent(event, channel) { return {event:null,channel}; }", request()).getEventJson());
        assertEquals("[\"value\",42]", WebhookScriptProcessor.process(
                "function onEvent(event, channel) { return {event:['value',42],channel}; }", request()).getEventJson());
        assertThrows(IllegalArgumentException.class, () -> new WebhookRequest(null, request().getChannel()));
    }

    @Test
    public void rawEventTextIsDataNotExecutableSource() {
        String raw = "{\"text\":\"'); throw new Error('injected'); //\"}";
        WebhookRequest original = new WebhookRequest(raw, request().getChannel());
        assertEquals(raw, WebhookScriptProcessor.process(IDENTITY, original).getEventJson());
    }

    @Test
    public void scriptCannotReplaceTheBoundaryJsonCodec() {
        String source = "JSON.parse = function() { return {}; }; JSON.stringify = function() { return 'wrong'; };" + IDENTITY;
        assertEquals(EVENT, WebhookScriptProcessor.process(source, request()).getEventJson());
    }

    @Test
    public void globalsAndMutationsDoNotCrossDeliveries() {
        String source = "var count = (typeof count === 'undefined' ? 0 : count) + 1;"
                + "function onEvent(event, channel) { return {event:{count:count},channel}; }";
        assertEquals("{\"count\":1}", WebhookScriptProcessor.process(source, request()).getEventJson());
        assertEquals("{\"count\":1}", WebhookScriptProcessor.process(source, request()).getEventJson());
        assertEquals(EVENT, WebhookScriptProcessor.process(IDENTITY, request()).getEventJson());
    }

    @Test
    public void exposesOnlyJavascriptDataAndStandardBuiltins() {
        String source = "function onEvent(event, channel) { return {"
                + "event:[typeof Packages,typeof java,typeof JavaAdapter,typeof fetch,typeof console,typeof channel.targetUrl],channel}; }";
        assertEquals("[\"undefined\",\"undefined\",\"undefined\",\"undefined\",\"undefined\",\"undefined\"]",
                WebhookScriptProcessor.process(source, request()).getEventJson());
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.process(
                "function onEvent(event, channel) { return {event:java.lang.System.getProperty('java.version'),channel}; }", request()));
    }

    @Test
    public void requiredResultFieldsCannotComeFromPrototypes() {
        String source = "Object.prototype.event = {sent:true};"
                + "Object.prototype.channel = {name:'x',url:'https://dst.example/',headers:{}};"
                + "function onEvent(event, channel) { return {}; }";
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.process(source, request()));
        String channelSource = "Object.prototype.name='x'; Object.prototype.url='https://dst.example/'; Object.prototype.headers={};"
                + "function onEvent(event, channel) { return {event,channel:{}}; }";
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.process(channelSource, request()));
    }

    @Test
    public void optionalProxyDoesNotComeFromPrototype() {
        String source = "Object.prototype.proxyUrl='http://127.0.0.1:9999';"
                + "function onEvent(event, channel) { return {event,channel:{name:channel.name,url:channel.url,headers:channel.headers}}; }";
        assertEquals("", WebhookScriptProcessor.process(source, request()).getChannel().getProxyUrl());
    }

    @Test
    public void malformedResultsDoNotFallBackToOriginal() {
        String[] bodies = {
                "return null;",
                "return event;",
                "return {channel};",
                "return {event:undefined,channel};",
                "return {event,channel:{}};",
                "channel.url = 'file:///tmp/data'; return {event,channel};",
                "channel.url = 42; return {event,channel};",
                "channel.proxyUrl = 'socks5://127.0.0.1:1080'; return {event,channel};",
                "channel.headers = {X:42}; return {event,channel};",
                "channel.headers = []; return {event,channel};",
                "channel.headers.Host = 'other.example'; return {event,channel};",
                "channel.headers.X = 'one\\r\\ntwo'; return {event,channel};",
                "event.self = event; return {event,channel};",
        };
        for (String body : bodies) {
            assertThrows(body, IllegalArgumentException.class, () -> WebhookScriptProcessor.process(
                    "function onEvent(event, channel) {" + body + "}", request()));
        }
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.process("var value = 1;", request()));
        assertEquals(EVENT, WebhookScriptProcessor.process(IDENTITY, request()).getEventJson());
    }

    @Test(timeout = 3000)
    public void validationCompilesWithoutExecutingGlobalCode() {
        WebhookScriptProcessor.validate("while (true) {} " + IDENTITY);
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.validate("function onEvent( {"));
    }

    @Test(timeout = 5000)
    public void loopsCannotSwallowExecutionLimit() {
        String[] scripts = {
                "while (true) {} " + IDENTITY,
                "function onEvent(event, channel) { try { while(true) {} } catch (error) { return {event,channel}; } }",
                "function onEvent(event, channel) { try { while(true) {} } finally { return {event,channel}; } }",
        };
        for (String source : scripts) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> WebhookScriptProcessor.process(source, request()));
            assertTrue(error.getMessage().contains("limit"));
        }
        assertEquals(EVENT, WebhookScriptProcessor.process(IDENTITY, request()).getEventJson());
    }

    @Test
    public void limitsSourceAndJsonSizes() {
        String source = new String(new char[WebhookChannel.MAX_SCRIPT_LENGTH + 1]).replace('\0', ' ');
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.validate(source));
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.process(source, request()));
        String huge = "\"" + new String(new char[WebhookScriptProcessor.MAX_JSON_BYTES]).replace('\0', 'x') + "\"";
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.process(
                IDENTITY, new WebhookRequest(huge, request().getChannel())));
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.process(
                "function onEvent(event, channel) { return {event:'x'.repeat(1048577),channel}; }", request()));
    }

    @Test
    public void inputLimitAlsoIncludesChannelFields() {
        String hugeUrl = "https://original.example/?q="
                + new String(new char[WebhookScriptProcessor.MAX_JSON_BYTES]).replace('\0', 'x');
        WebhookRequest original = WebhookRequest.from(new WebhookChannel("ops", hugeUrl, "", ""), "{}");
        String source = "function onEvent(event, channel) { channel.url = 'https://short.example/'; return {event,channel}; }";
        assertThrows(IllegalArgumentException.class, () -> WebhookScriptProcessor.process(source, original));
    }

    @Test
    public void runtimeErrorMessagesDoNotEchoPayloads() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WebhookScriptProcessor.process(
                        "function onEvent(event, channel) { throw new Error('private-credential'); }", request()));
        assertFalse(error.getMessage().contains("private-credential"));
    }
}
