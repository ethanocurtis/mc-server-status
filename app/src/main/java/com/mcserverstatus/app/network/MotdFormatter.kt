package com.mcserverstatus.app.network

import org.json.JSONArray
import org.json.JSONObject

/** One run of MOTD text sharing the same color/style, ready for UI rendering. */
data class MotdSegment(
    val text: String,
    val colorArgb: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
)

/**
 * Parses a Minecraft "chat component" MOTD (either a plain legacy string
 * using section-sign `§` formatting codes, or a modern JSON chat component
 * with a `text`/`extra`/`color` tree - servers can send either, and modern
 * servers frequently mix both by embedding legacy codes inside JSON text
 * nodes) into a flat list of styled [MotdSegment]s.
 */
object MotdFormatter {

    // Standard 16 Minecraft legacy colors, in ARGB (opaque).
    private val LEGACY_COLORS: Map<Char, Int> = mapOf(
        '0' to 0xFF000000.toInt(), '1' to 0xFF0000AA.toInt(), '2' to 0xFF00AA00.toInt(),
        '3' to 0xFF00AAAA.toInt(), '4' to 0xFFAA0000.toInt(), '5' to 0xFFAA00AA.toInt(),
        '6' to 0xFFFFAA00.toInt(), '7' to 0xFFAAAAAA.toInt(), '8' to 0xFF555555.toInt(),
        '9' to 0xFF5555FF.toInt(), 'a' to 0xFF55FF55.toInt(), 'b' to 0xFF55FFFF.toInt(),
        'c' to 0xFFFF5555.toInt(), 'd' to 0xFFFF55FF.toInt(), 'e' to 0xFFFFFF55.toInt(),
        'f' to 0xFFFFFFFF.toInt(),
    )

    // Named colors as used by the modern "color" JSON field.
    private val NAMED_COLORS: Map<String, Int> = mapOf(
        "black" to LEGACY_COLORS['0']!!, "dark_blue" to LEGACY_COLORS['1']!!,
        "dark_green" to LEGACY_COLORS['2']!!, "dark_aqua" to LEGACY_COLORS['3']!!,
        "dark_red" to LEGACY_COLORS['4']!!, "dark_purple" to LEGACY_COLORS['5']!!,
        "gold" to LEGACY_COLORS['6']!!, "gray" to LEGACY_COLORS['7']!!,
        "grey" to LEGACY_COLORS['7']!!, "dark_gray" to LEGACY_COLORS['8']!!,
        "dark_grey" to LEGACY_COLORS['8']!!, "blue" to LEGACY_COLORS['9']!!,
        "green" to LEGACY_COLORS['a']!!, "aqua" to LEGACY_COLORS['b']!!,
        "red" to LEGACY_COLORS['c']!!, "light_purple" to LEGACY_COLORS['d']!!,
        "yellow" to LEGACY_COLORS['e']!!, "white" to LEGACY_COLORS['f']!!,
    )

    private class Style(
        var color: Int? = null,
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var strikethrough: Boolean = false,
    ) {
        fun copy() = Style(color, bold, italic, underline, strikethrough)
    }

    /** Parses the raw `description` value from a status JSON response (a [String], [JSONObject], or [JSONArray]). */
    fun parseDescription(raw: Any?): List<MotdSegment> {
        val segments = mutableListOf<MotdSegment>()
        appendNode(raw, Style(), segments)
        return if (segments.isEmpty()) listOf(MotdSegment("")) else segments
    }

    /** Parses a Bedrock-style plain motd line, honoring `§` legacy codes only. */
    fun parseLegacyLine(raw: String): List<MotdSegment> {
        val segments = mutableListOf<MotdSegment>()
        appendLegacyText(raw, Style(), segments)
        return if (segments.isEmpty()) listOf(MotdSegment("")) else segments
    }

    fun toPlainText(segments: List<MotdSegment>): String = segments.joinToString(separator = "") { it.text }

    private fun appendNode(node: Any?, inherited: Style, out: MutableList<MotdSegment>) {
        when (node) {
            null, JSONObject.NULL -> return
            is String -> appendLegacyText(node, inherited, out)
            is JSONObject -> {
                val style = inherited.copy()
                if (node.has("color")) {
                    val colorValue = node.optString("color", "")
                    style.color = resolveColor(colorValue) ?: style.color
                }
                if (node.has("bold")) style.bold = node.optBoolean("bold")
                if (node.has("italic")) style.italic = node.optBoolean("italic")
                if (node.has("underlined")) style.underline = node.optBoolean("underlined")
                if (node.has("strikethrough")) style.strikethrough = node.optBoolean("strikethrough")

                if (node.has("text")) appendLegacyText(node.optString("text", ""), style, out)

                val extra = node.optJSONArray("extra")
                if (extra != null) {
                    for (i in 0 until extra.length()) {
                        appendNode(extra.get(i), style, out)
                    }
                }

                // Some servers omit "text" but only provide "translate" - fall back to that key
                // so we at least show something instead of silently dropping the node.
                if (!node.has("text") && node.has("translate")) {
                    appendLegacyText(node.optString("translate", ""), style, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    appendNode(node.get(i), inherited, out)
                }
            }
            else -> appendLegacyText(node.toString(), inherited, out)
        }
    }

    /** Splits [text] on `§`-prefixed legacy formatting codes, applying them on top of [base]. */
    private fun appendLegacyText(text: String, base: Style, out: MutableList<MotdSegment>) {
        if (text.isEmpty()) return
        var style = base.copy()
        val builder = StringBuilder()

        fun flush() {
            if (builder.isNotEmpty()) {
                out += MotdSegment(
                    text = builder.toString(),
                    colorArgb = style.color,
                    bold = style.bold,
                    italic = style.italic,
                    underline = style.underline,
                    strikethrough = style.strikethrough,
                )
                builder.setLength(0)
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if ((c == '§' || c == '&') && i + 1 < text.length && isLegacyCode(text[i + 1])) {
                flush()
                val code = text[i + 1].lowercaseChar()
                when (code) {
                    'r' -> style = Style()
                    'l' -> style.bold = true
                    'o' -> style.italic = true
                    'n' -> style.underline = true
                    'm' -> style.strikethrough = true
                    'k' -> { /* obfuscated: not rendered, treated as a no-op */ }
                    else -> LEGACY_COLORS[code]?.let {
                        // A color code resets other formatting, matching vanilla behavior.
                        style = Style(color = it)
                    }
                }
                i += 2
            } else {
                builder.append(c)
                i++
            }
        }
        flush()
    }

    private fun isLegacyCode(c: Char): Boolean {
        val lower = c.lowercaseChar()
        return lower in '0'..'9' || lower in 'a'..'f' || lower in "klmnor"
    }

    private fun resolveColor(value: String): Int? {
        if (value.isEmpty()) return null
        if (value.startsWith("#") && value.length == 7) {
            return runCatching { (0xFF000000.toInt()) or (value.substring(1).toLong(16).toInt()) }.getOrNull()
        }
        return NAMED_COLORS[value.lowercase()]
    }
}
