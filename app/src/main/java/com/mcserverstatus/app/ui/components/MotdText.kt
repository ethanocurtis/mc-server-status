package com.mcserverstatus.app.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.mcserverstatus.app.network.MotdSegment

/** Renders a parsed MOTD ([MotdSegment] list) with its original colors/styling. */
@Composable
fun MotdText(
    segments: List<MotdSegment>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val fallbackColor = LocalContentColor.current
    val annotated = buildAnnotatedString {
        for (segment in segments) {
            val decorations = buildList {
                if (segment.underline) add(TextDecoration.Underline)
                if (segment.strikethrough) add(TextDecoration.LineThrough)
            }
            withStyle(
                SpanStyle(
                    color = segment.colorArgb?.let { Color(it) } ?: fallbackColor,
                    fontWeight = if (segment.bold) FontWeight.Bold else null,
                    fontStyle = if (segment.italic) FontStyle.Italic else null,
                    textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                ),
            ) {
                append(segment.text)
            }
        }
    }
    Text(text = annotated, modifier = modifier, maxLines = maxLines, overflow = overflow)
}
