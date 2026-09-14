package com.cclilshy.tayc.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import com.cclilshy.tayc.R
import com.cclilshy.tayc.webhook.domain.WebhookChannel

private const val SCRIPT_TEMPLATE = "function onEvent(event, channel) {\n    return { event, channel };\n}"
private val SCRIPT_TOKENS = Regex(
    """//[^\r\n]*|/\*[\s\S]*?(?:\*/|$)|"(?:\\[\s\S]|[^"\\\r\n])*"?|'(?:\\[\s\S]|[^'\\\r\n])*'?|`(?:\\[\s\S]|[^`\\])*`?|\b(?:0[xX][\da-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b|\b(?:function|return|const|let|var|if|else|switch|case|break|continue|for|of|in|while|do|try|catch|finally|throw|new|typeof|instanceof|void|delete|this|true|false|null|undefined|class|extends|async|await)\b""",
)

internal class JavaScriptHighlighting(
    private val keyword: Color,
    private val string: Color,
    private val comment: Color,
    private val number: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val result = AnnotatedString.Builder(text)
        if (text.length <= WebhookChannel.MAX_SCRIPT_LENGTH) {
            SCRIPT_TOKENS.findAll(text.text).forEach { token ->
                val value = token.value
                val color = when {
                    value.startsWith("//") || value.startsWith("/*") -> comment
                    value.first() in "\"'`" -> string
                    value.first().isDigit() -> number
                    else -> keyword
                }
                result.addStyle(SpanStyle(color = color), token.range.first, token.range.last + 1)
            }
        }
        return TransformedText(result.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun JavaScriptEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highlighting = remember(colors.primary, colors.tertiary, colors.onSurfaceVariant, colors.secondary) {
        JavaScriptHighlighting(colors.primary, colors.tertiary, colors.onSurfaceVariant, colors.secondary)
    }
    val tooLong = value.length > WebhookChannel.MAX_SCRIPT_LENGTH
    val codeStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.javascript_optional)) },
            placeholder = { Text(SCRIPT_TEMPLATE, style = codeStyle) },
            textStyle = codeStyle,
            visualTransformation = highlighting,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
            ),
            minLines = 6,
            maxLines = 12,
            isError = tooLong,
            supportingText = {
                Text(
                    if (tooLong) {
                        stringResource(R.string.javascript_too_long, WebhookChannel.MAX_SCRIPT_LENGTH)
                    } else {
                        stringResource(R.string.javascript_contract)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("webhook-script-editor"),
        )
        if (value.isBlank()) {
            TextButton(onClick = { onValueChange(SCRIPT_TEMPLATE) }) {
                Text(stringResource(R.string.insert_script_template))
            }
        }
    }
}
