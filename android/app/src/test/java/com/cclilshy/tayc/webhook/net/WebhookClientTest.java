package com.cclilshy.tayc.webhook.net;

import com.cclilshy.tayc.webhook.domain.WebhookChannel;
import com.cclilshy.tayc.webhook.domain.WebhookRequest;
import com.cclilshy.tayc.webhook.runtime.WebhookScriptProcessor;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WebhookClientTest {
    @Test
    public void defaultContentTypeIsJson() {
        assertEquals("application/json; charset=utf-8", WebhookClient.jsonContentType());
    }

    @Test
    public void createsBasicProxyAuthorizationHeaderWhenProxyUrlHasCredentials() throws Exception {
        assertEquals(
                "Basic YWxpY2U6c2VjcmV0",
                WebhookClient.proxyAuthorizationHeader("http://alice:secret@127.0.0.1:8080"));
    }

    @Test
    public void omitsProxyAuthorizationHeaderWhenProxyUrlHasNoCredentials() throws Exception {
        assertEquals("", WebhookClient.proxyAuthorizationHeader("http://127.0.0.1:8080"));
        assertEquals("", WebhookClient.proxyAuthorizationHeader(""));
    }

    @Test(timeout = 5000)
    public void sendsUnscriptedRawJsonWithoutReformatting() throws Exception {
        try (Endpoint endpoint = new Endpoint(204, null)) {
            String event = " {\"type\": \"sms\", \"data\": {\"text\": \"hello\"}}\n";
            WebhookChannel channel = new WebhookChannel("ops", endpoint.url("/raw"), "", "");
            assertEquals(204, deliver(channel, event));
            Received received = endpoint.take();
            assertEquals("POST", received.method);
            assertEquals("/raw", received.uri);
            assertEquals(event, received.body);
            assertEquals(WebhookClient.jsonContentType(), received.headers.get("Content-Type"));
            assertEquals("application/json", received.headers.get("Accept"));
        }
    }

    @Test(timeout = 5000)
    public void sendsOnlyReturnedEventToReturnedChannel() throws Exception {
        try (Endpoint endpoint = new Endpoint(200, null)) {
            String script = "function onEvent(event, channel) {"
                    + "channel.url = '" + endpoint.url("/transformed") + "';"
                    + "channel.headers = {'Content-Type':'application/custom+json','X-Event':event.type};"
                    + "return {event:{message:event.data.text},channel}; }";
            WebhookChannel channel = new WebhookChannel("ops", "http://127.0.0.1:1/not-used", "", script);
            assertEquals(200, deliver(channel, "{\"type\":\"sms\",\"data\":{\"text\":\"hello\"}}"));
            Received received = endpoint.take();
            assertEquals("/transformed", received.uri);
            assertEquals("{\"message\":\"hello\"}", received.body);
            assertEquals("sms", received.headers.get("X-Event"));
            assertEquals("application/custom+json", received.headers.get("Content-Type"));
            assertNotEquals("application/json", received.headers.get("Accept"));
            assertEquals("http://127.0.0.1:1/not-used", channel.getTargetUrl());
        }
    }

    @Test(timeout = 5000)
    public void usesReturnedProxyAndItsAuthentication() throws Exception {
        try (Endpoint proxy = new Endpoint(200, null)) {
            String script = "function onEvent(event, channel) {"
                    + "channel.url = 'http://upstream.invalid/event';"
                    + "channel.proxyUrl = 'http://alice:secret@127.0.0.1:" + proxy.port() + "';"
                    + "return {event,channel}; }";
            WebhookChannel channel = new WebhookChannel("ops", "http://original.invalid", "", script);
            assertEquals(200, deliver(channel, "{\"ok\":true}"));
            Received received = proxy.take();
            assertEquals("http://upstream.invalid/event", received.uri);
            assertEquals("Basic YWxpY2U6c2VjcmV0", received.headers.get("Proxy-Authorization"));
            assertEquals("{\"ok\":true}", received.body);
            assertEquals("", channel.getProxyUrl());
        }
    }

    @Test(timeout = 5000)
    public void httpsProxyNeverReceivesPlaintextHttp() throws Exception {
        try (Endpoint proxy = new Endpoint(200, null)) {
            WebhookChannel channel = new WebhookChannel("ops", "http://upstream.invalid/event",
                    "https://127.0.0.1:" + proxy.port(), "");
            assertThrows(java.io.IOException.class, () -> deliver(channel, "{}"));
            assertTrue(proxy.requests.isEmpty());
        }
    }

    @Test(timeout = 5000)
    public void sendsExplicitJsonNull() throws Exception {
        try (Endpoint endpoint = new Endpoint(200, null)) {
            WebhookChannel channel = new WebhookChannel("ops", endpoint.url("/"), "",
                    "function onEvent(event, channel) { return {event:null,channel}; }");
            assertEquals(200, deliver(channel, "{}"));
            assertEquals("null", endpoint.take().body);
        }
    }

    @Test(timeout = 5000)
    public void failedScriptDoesNotSendAndNextChannelStillWorks() throws Exception {
        try (Endpoint endpoint = new Endpoint(200, null)) {
            WebhookChannel bad = new WebhookChannel("bad", endpoint.url("/bad"), "",
                    "function onEvent(event, channel) { throw new Error('fail'); }");
            assertThrows(IllegalArgumentException.class, () -> deliver(bad, "{}"));
            assertTrue(endpoint.requests.isEmpty());
            WebhookChannel good = new WebhookChannel("good", endpoint.url("/good"), "", "");
            assertEquals(200, deliver(good, "{}"));
            assertEquals("/good", endpoint.take().uri);
        }
    }

    @Test(timeout = 5000)
    public void doesNotRedirectTheFinalRequest() throws Exception {
        try (Endpoint other = new Endpoint(200, null);
             Endpoint endpoint = new Endpoint(302, other.url("/redirected"))) {
            WebhookChannel channel = new WebhookChannel("ops", endpoint.url("/"), "", "");
            assertEquals(302, deliver(channel, "{}"));
            endpoint.take();
            assertTrue(other.requests.isEmpty());
        }
    }

    private static int deliver(WebhookChannel channel, String event) throws Exception {
        WebhookRequest request = WebhookRequest.from(channel, event);
        return WebhookClient.postJson(WebhookScriptProcessor.process(channel.getScript(), request));
    }

    private static final class Received {
        final String method;
        final String uri;
        final Map<String, String> headers;
        final String body;

        Received(String method, String uri, Map<String, String> headers, String body) {
            this.method = method;
            this.uri = uri;
            this.headers = headers;
            this.body = body;
        }
    }

    private static final class Endpoint implements AutoCloseable {
        final ServerSocket server = new ServerSocket();
        final ArrayBlockingQueue<Received> requests = new ArrayBlockingQueue<>(4);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread worker;
        volatile boolean closing;

        Endpoint(int responseCode, String location) throws Exception {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            worker = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000);
                    InputStream input = new BufferedInputStream(socket.getInputStream());
                    String[] request = line(input).split(" ", 3);
                    Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    String header;
                    while (!(header = line(input)).isEmpty()) {
                        int colon = header.indexOf(':');
                        headers.put(header.substring(0, colon), header.substring(colon + 1).trim());
                    }
                    byte[] body = new byte[Integer.parseInt(headers.get("Content-Length"))];
                    int offset = 0;
                    while (offset < body.length) {
                        int count = input.read(body, offset, body.length - offset);
                        if (count < 0) throw new EOFException();
                        offset += count;
                    }
                    requests.offer(new Received(request[0], request[1], headers, new String(body, StandardCharsets.UTF_8)));
                    String response = "HTTP/1.1 " + responseCode + " Test\r\nContent-Length: 0\r\nConnection: close\r\n"
                            + (location == null ? "" : "Location: " + location + "\r\n") + "\r\n";
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (Exception error) {
                    if (!closing) failure.set(error);
                }
            }, "webhook-test-http");
            worker.setDaemon(true);
            worker.start();
        }

        int port() {
            return server.getLocalPort();
        }

        String url(String path) {
            return "http://127.0.0.1:" + port() + path;
        }

        Received take() throws Exception {
            Received value = requests.poll(2, TimeUnit.SECONDS);
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertNotNull("HTTP request was not received", value);
            return value;
        }

        private static String line(InputStream input) throws Exception {
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            int ch;
            while ((ch = input.read()) != '\n') {
                if (ch < 0) throw new EOFException();
                if (ch != '\r') text.write(ch);
            }
            return new String(text.toByteArray(), StandardCharsets.US_ASCII);
        }

        @Override
        public void close() throws Exception {
            closing = true;
            server.close();
            worker.join(1000);
        }
    }
}
