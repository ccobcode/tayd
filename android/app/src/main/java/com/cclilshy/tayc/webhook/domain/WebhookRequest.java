package com.cclilshy.tayc.webhook.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class WebhookRequest {
    public static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final String eventJson;
    private final Channel channel;

    public WebhookRequest(String eventJson, Channel channel) {
        if (eventJson == null || eventJson.trim().isEmpty()) {
            throw new IllegalArgumentException("event JSON is required");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        this.eventJson = eventJson;
        this.channel = channel;
    }

    public static WebhookRequest from(WebhookChannel channel, String eventJson) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", JSON_CONTENT_TYPE);
        headers.put("Accept", "application/json");
        return new WebhookRequest(eventJson, new Channel(
                channel.getName(), channel.getTargetUrl(), channel.getProxyUrl(), headers));
    }

    public String getEventJson() {
        return eventJson;
    }

    public Channel getChannel() {
        return channel;
    }

    public static final class Channel {
        private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

        private final String name;
        private final String url;
        private final String proxyUrl;
        private final Map<String, String> headers;

        public Channel(String name, String url, String proxyUrl, Map<String, String> headers) {
            this.name = WebhookChannel.requireName(name);
            this.url = WebhookChannel.requireHttpUrl(url, "channel.url");
            this.proxyUrl = proxyUrl == null || proxyUrl.trim().isEmpty()
                    ? ""
                    : WebhookChannel.requireHttpUrl(proxyUrl, "channel.proxyUrl");
            if (headers == null || headers.size() > 64) {
                throw new IllegalArgumentException("channel.headers must contain at most 64 headers");
            }
            Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                validateHeader(header.getKey(), header.getValue());
                copy.put(header.getKey(), header.getValue());
            }
            this.headers = Collections.unmodifiableMap(copy);
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public String getProxyUrl() {
            return proxyUrl;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        private static void validateHeader(String name, String value) {
            if (name == null || name.length() > 128 || !HEADER_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("invalid HTTP header name");
            }
            switch (name.toLowerCase(Locale.ROOT)) {
                case "host":
                case "content-length":
                case "transfer-encoding":
                case "connection":
                case "proxy-authorization":
                    throw new IllegalArgumentException("HTTP transport manages this header: " + name);
                default:
                    break;
            }
            if (value == null || value.length() > 8192) {
                throw new IllegalArgumentException("invalid HTTP header value");
            }
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if ((ch < 0x20 && ch != '\t') || ch == 0x7f) {
                    throw new IllegalArgumentException("invalid HTTP header value");
                }
            }
        }
    }
}
