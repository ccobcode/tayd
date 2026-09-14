package com.cclilshy.tayc.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cclilshy.tayc.R
import com.cclilshy.tayc.app.MainActivity
import com.cclilshy.tayc.gateway.data.GatewayPrefs
import com.cclilshy.tayc.webhook.data.WebhookChannelStore
import com.cclilshy.tayc.webhook.runtime.WebhookDispatcher
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.TreeMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebhookEditorInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun editedScriptIsSavedAndControlsTheRealAndroidRequest() {
        val context = compose.activity.applicationContext
        val prefs = GatewayPrefs.get(context)
        val oldChannels = prefs.getString(GatewayPrefs.KEY_WEBHOOK_CHANNELS, null)
        val oldSubscription = prefs.getString(GatewayPrefs.KEY_EVENT_SMS_WEBHOOK_CHANNELS, null)
        val hadEnabled = prefs.contains(GatewayPrefs.KEY_WEBHOOK_ENABLED)
        val oldEnabled = prefs.getBoolean(GatewayPrefs.KEY_WEBHOOK_ENABLED, false)
        val received = CompletableFuture<Received>()
        val server = ServerSocket().apply {
            bind(InetSocketAddress("127.0.0.1", 0))
            soTimeout = 60000
        }
        val worker = thread(isDaemon = true, name = "android-webhook-test") {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = BufferedInputStream(socket.getInputStream())
                    val request = line(input).split(' ', limit = 3)
                    val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
                    var header = line(input)
                    while (header.isNotEmpty()) {
                        val colon = header.indexOf(':')
                        headers[header.substring(0, colon)] = header.substring(colon + 1).trim()
                        header = line(input)
                    }
                    val body = ByteArray(headers.getValue("Content-Length").toInt())
                    DataInputStream(input).readFully(body)
                    socket.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray(),
                    )
                    socket.getOutputStream().flush()
                    received.complete(Received(request[0], request[1], headers, body.toString(Charsets.UTF_8)))
                }
            } catch (error: Exception) {
                received.completeExceptionally(error)
            }
        }
        val name = "js-ui-${System.nanoTime()}"
        val before = "http://127.0.0.1:${server.localPort}/before"
        val after = "http://127.0.0.1:${server.localPort}/after"
        val source = """
            function onEvent(event, channel) {
                // 中间件 😀
                channel.url = "$after";
                channel.headers["X-TayC-Test"] = "android";
                return {
                    event: { source: event.type, message: event.data.text },
                    channel: channel
                };
            }
        """.trimIndent()
        try {
            capture("home.png")
            compose.onNodeWithText(context.getString(R.string.nav_services)).performClick()
            compose.onNodeWithText(context.getString(R.string.extension_webhook)).performClick()
            capture("webhook-detail.png")
            compose.onNodeWithContentDescription(context.getString(R.string.add_channel)).performClick()
            compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.field_name)))
                .performTextReplacement(name)
            compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.target_url)))
                .performTextReplacement(before)
            val editor = compose.onNodeWithTag("webhook-script-editor")
            editor.performScrollTo().performTextReplacement(source)
            editor.assertTextContains(source, substring = false)
            capture("webhook-editor.png")
            compose.onNodeWithText(context.getString(R.string.save_channel)).performScrollTo().performClick()
            compose.waitUntil(5000) {
                WebhookChannelStore.find(prefs.getString(GatewayPrefs.KEY_WEBHOOK_CHANNELS, ""), name) != null
            }
            val saved = WebhookChannelStore.find(prefs.getString(GatewayPrefs.KEY_WEBHOOK_CHANNELS, ""), name)
            assertNotNull(saved)
            assertEquals(source, saved.script)
            assertEquals(before, saved.targetUrl)
            prefs.edit()
                .putBoolean(GatewayPrefs.KEY_WEBHOOK_ENABLED, true)
                .putString(GatewayPrefs.KEY_EVENT_SMS_WEBHOOK_CHANNELS, name)
                .commit()
            WebhookDispatcher.dispatch(context, "sms", """{"type":"sms","data":{"text":"device event"}}""")
            val request = received.get(10, TimeUnit.SECONDS)
            assertEquals("POST", request.method)
            assertEquals("/after", request.path)
            assertEquals("android", request.headers["X-TayC-Test"])
            assertEquals("""{"source":"sms","message":"device event"}""", request.body)
            val unchanged = WebhookChannelStore.find(prefs.getString(GatewayPrefs.KEY_WEBHOOK_CHANNELS, ""), name)
            assertEquals(before, unchanged.targetUrl)
            assertEquals(source, unchanged.script)
        } finally {
            server.close()
            worker.join(1000)
            val edit = prefs.edit()
                .putString(GatewayPrefs.KEY_WEBHOOK_CHANNELS, oldChannels)
                .putString(GatewayPrefs.KEY_EVENT_SMS_WEBHOOK_CHANNELS, oldSubscription)
            if (hadEnabled) edit.putBoolean(GatewayPrefs.KEY_WEBHOOK_ENABLED, oldEnabled)
            else edit.remove(GatewayPrefs.KEY_WEBHOOK_ENABLED)
            edit.commit()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        checkNotNull(bitmap)
        File(compose.activity.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun line(input: InputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val ch = input.read()
            if (ch < 0) throw EOFException()
            if (ch == '\n'.code) return out.toString("US-ASCII")
            if (ch != '\r'.code) out.write(ch)
        }
    }

    private data class Received(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )
}
