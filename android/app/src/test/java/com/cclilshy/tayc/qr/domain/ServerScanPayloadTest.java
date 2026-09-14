package com.cclilshy.tayc.qr.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServerScanPayloadTest {
    @Test
    public void parsesTayCServerQrPayload() {
        ServerScanPayload payload = ServerScanPayload.parse(
                "tayc://server?addr=frp.example.com&port=7000&token=test-token");

        assertEquals("frp.example.com", payload.getServer());
        assertEquals(7000, payload.getPort());
        assertEquals("test-token", payload.getToken());
    }

    @Test
    public void usesDefaultServerPort() {
        ServerScanPayload payload = ServerScanPayload.parse(
                "tayc://server?addr=frp.example.com&token=test-token");

        assertEquals(7000, payload.getPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonTayCPayloads() {
        ServerScanPayload.parse("https://example.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLegacyScheme() {
        ServerScanPayload.parse("tayd://server?addr=frp.example.com&port=7000&token=test-token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsServerAddressAlias() {
        ServerScanPayload.parse("tayc://server?server=frp.example.com&port=7000&token=test-token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankAddress() {
        ServerScanPayload.parse("tayc://server?addr=%20&port=7000&token=test-token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankToken() {
        ServerScanPayload.parse("tayc://server?addr=frp.example.com&port=7000&token=%20");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangePort() {
        ServerScanPayload.parse("tayc://server?addr=frp.example.com&port=65536&token=test-token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidPort() {
        ServerScanPayload.parse("tayc://server?addr=frp.example.com&port=invalid&token=test-token");
    }
}
