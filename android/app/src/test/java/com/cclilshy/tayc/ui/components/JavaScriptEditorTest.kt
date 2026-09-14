package com.cclilshy.tayc.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.cclilshy.tayc.webhook.domain.WebhookChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaScriptEditorTest {
    private val keyword = Color.Blue
    private val string = Color.Green
    private val comment = Color.Gray
    private val number = Color.Red
    private val highlighting = JavaScriptHighlighting(keyword, string, comment, number)

    @Test
    fun preservesTextAndEveryCursorOffset() {
        val source = "function onEvent(event, channel) {\n\t// 中文 😀\n\treturn {event, channel};\n}"
        val transformed = highlighting.filter(AnnotatedString(source))
        assertEquals(source, transformed.text.text)
        for (offset in 0..source.length) {
            assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
            assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
        }
    }

    @Test
    fun doesNotHighlightKeywordsInsideStringsOrComments() {
        val source = "const text = \"return\"; // if true\nlet count = 42;"
        val text = highlighting.filter(AnnotatedString(source)).text
        fun colorAt(offset: Int): Color = text.spanStyles.last { offset in it.start until it.end }.item.color
        assertEquals(keyword, colorAt(source.indexOf("const")))
        assertEquals(string, colorAt(source.indexOf("return")))
        assertEquals(comment, colorAt(source.indexOf("if true")))
        assertEquals(number, colorAt(source.indexOf("42")))
    }

    @Test
    fun handlesIncompleteMultilineCommentsAndTemplateStrings() {
        val commentText = highlighting.filter(AnnotatedString("/* comment\nreturn value")).text
        assertEquals(1, commentText.spanStyles.size)
        assertEquals(comment, commentText.spanStyles.single().item.color)
        val template = "`message \${event.type}\nnext line`"
        val text = highlighting.filter(AnnotatedString(template)).text
        assertEquals(template, text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals(string, text.spanStyles.single().item.color)
    }

    @Test
    fun oversizedInputRemainsEditableWithoutTokenizing() {
        val source = "x".repeat(WebhookChannel.MAX_SCRIPT_LENGTH + 1)
        val text = highlighting.filter(AnnotatedString(source)).text
        assertEquals(source, text.text)
        assertTrue(text.spanStyles.isEmpty())
    }
}
