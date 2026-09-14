package com.cclilshy.tayc.webhook.net;

import com.cclilshy.tayc.webhook.domain.WebhookRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class WebhookClient {
    private WebhookClient() {
    }

    public static String jsonContentType() {
        return WebhookRequest.JSON_CONTENT_TYPE;
    }

    public static int postJson(WebhookRequest request) throws IOException {
        WebhookRequest.Channel channel = request.getChannel();
        URL url = new URL(channel.getUrl());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxyFor(channel.getProxyUrl()));
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            for (Map.Entry<String, String> header : channel.getHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            String proxyAuthorization = proxyAuthorizationHeader(channel.getProxyUrl());
            if (!proxyAuthorization.isEmpty()) {
                connection.setRequestProperty("Proxy-Authorization", proxyAuthorization);
            }
            byte[] body = request.getEventJson().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(body);
            }
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static Proxy proxyFor(String proxyUrl) throws IOException {
        if (proxyUrl == null || proxyUrl.trim().isEmpty()) {
            return Proxy.NO_PROXY;
        }
        URL url = new URL(proxyUrl);
        if (!"http".equals(url.getProtocol())) {
            throw new IOException("proxyUrl must use http; TLS connections to proxies are not supported");
        }
        int port = url.getPort() >= 0 ? url.getPort() : 80;
        if (port == 0 || port > 65535) {
            throw new IOException("invalid proxy port");
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(url.getHost(), port));
    }

    public static String proxyAuthorizationHeader(String proxyUrl) throws IOException {
        if (proxyUrl == null || proxyUrl.trim().isEmpty()) {
            return "";
        }
        try {
            String userInfo = new URI(proxyUrl).getUserInfo();
            if (userInfo == null || userInfo.isEmpty()) {
                return "";
            }
            return "Basic " + Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));
        } catch (URISyntaxException err) {
            throw new IOException("invalid proxy URL", err);
        }
    }
}
