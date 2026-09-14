package com.cclilshy.tayc.webhook.data;

import com.cclilshy.tayc.webhook.domain.WebhookChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

public class WebhookChannelStoreTest {
    @Test
    public void serializesWebhookChannelsWithTargetAndOptionalProxy() {
        String stored = WebhookChannelStore.serialize(WebhookChannelStore.upsert(
                "",
                new WebhookChannel("ops", "https://hooks.example.com/events", "http://127.0.0.1:8080", "")));

        List<WebhookChannel> channels = WebhookChannelStore.parse(stored);

        assertEquals(1, channels.size());
        assertEquals("ops", channels.get(0).getName());
        assertEquals("https://hooks.example.com/events", channels.get(0).getTargetUrl());
        assertEquals("http://127.0.0.1:8080", channels.get(0).getProxyUrl());
        assertEquals("", channels.get(0).getScript());
    }

    @Test
    public void optionalFieldsAreEmptyWhenUnspecified() {
        WebhookChannel channel = WebhookChannelStore.parse("ops\thttps://hooks.example.com/events").get(0);
        assertEquals("", channel.getProxyUrl());
        assertEquals("", channel.getScript());
    }

    @Test
    public void preservesScriptWhitespaceEscapesAndUnicode() {
        String source = "function onEvent(event, channel) {\r\n\t// 中文 😀\n\tconst value = '\\\\n';\n\treturn {event, channel};\n}\n";
        WebhookChannel channel = new WebhookChannel("ops", "https://hooks.example.com/events", "", source);
        String stored = WebhookChannelStore.serialize(WebhookChannelStore.upsert("", channel));
        assertEquals(1, stored.split("\n", -1).length);
        assertEquals(source, WebhookChannelStore.parse(stored).get(0).getScript());
    }

    @Test
    public void replacesScriptAndRemovesByName() {
        String stored = WebhookChannelStore.serialize(WebhookChannelStore.upsert(
                "", new WebhookChannel("ops", "https://old.example.com", "", "")));
        String source = "function onEvent(event, channel) { return {event, channel}; }";
        stored = WebhookChannelStore.serialize(WebhookChannelStore.upsert(
                stored, new WebhookChannel("ops", "https://new.example.com", "", source)));
        assertEquals(source, WebhookChannelStore.find(stored, "ops").getScript());
        stored = WebhookChannelStore.remove(stored, "ops");

        assertNull(WebhookChannelStore.find(stored, "ops"));
        assertEquals(0, WebhookChannelStore.parse(stored).size());
    }

    @Test
    public void rejectsOversizedScripts() {
        String source = new String(new char[WebhookChannel.MAX_SCRIPT_LENGTH + 1]);
        assertThrows(IllegalArgumentException.class,
                () -> new WebhookChannel("ops", "https://hooks.example.com", "", source));
    }
}
