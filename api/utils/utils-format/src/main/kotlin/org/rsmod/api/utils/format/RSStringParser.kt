
public enum class RsColor(public val tag: String, public val hex: String) {
    RED("red", "800000"),
    GREEN("green", "00ff00"),
    BLUE("blue", "000080"),
    WHITE("white", "ffffff"),
    YELLOW("yellow", "ffff00"),
    CYAN("cyan", "00ffff"),
    MAGENTA("magenta", "ff00ff"),
    BLACK("black", "000000");

    public companion object {
        private val HEX_COLOR_PATTERN = Regex("^[0-9a-fA-F]{3,8}$")

        public fun lookup(nameOrHex: String): String? {
            val key = nameOrHex.lowercase()
            for (c in entries) if (c.tag == key) return c.hex
            return if (HEX_COLOR_PATTERN.matches(key)) key else null
        }
    }
}

public enum class RsStyle(public val tag: String, public val rsBase: String) {
    STRIKE("strike", "str"),
    UNDERLINE("underline", "u"),
    SHADOW("shad", "shad");

    public companion object {
        public fun fromTag(tag: String): RsStyle? = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
    }
}

private data class ActiveTag(val type: String, val value: String?)

private val COLORED_STYLE_PATTERN = Regex("^(strike|underline|shad)=([a-z0-9]+)$")

public fun String.toRs(inheritPreviousTags: Boolean = true, wrapAt: Int? = null): String {
    val out = StringBuilder()
    val stack = ArrayDeque<ActiveTag>()
    var pendingRestore: ActiveTag? = null
    var i = 0

    while (i < length) {
        val ch = this[i]

        if (pendingRestore != null && inheritPreviousTags) {
            if (ch != '<' && !ch.isWhitespace()) {
                appendTag(out, pendingRestore!!)
                pendingRestore = null
            }
        }

        if (ch == '<') {
            val closeIndex = indexOf('>', i)
            if (closeIndex == -1) {
                out.append(substring(i))
                break
            }

            val rawTag = substring(i + 1, closeIndex)
            val tagContent = rawTag.lowercase().trim()
            i = closeIndex + 1

            if (tagContent.startsWith("/")) {
                val popped = stack.removeLastOrNull()
                if (popped != null) out.append(closeTag(popped))
                pendingRestore = if (inheritPreviousTags) stack.lastOrNull() else null
                continue
            }

            val styleMatch = COLORED_STYLE_PATTERN.matchEntire(tagContent)
            val style = RsStyle.fromTag(styleMatch?.groupValues?.get(1) ?: tagContent)
            val activeTag =
                if (style != null) {
                    val color = styleMatch?.groupValues?.get(2)?.let { RsColor.lookup(it) ?: it }
                    ActiveTag(style.rsBase, color)
                } else {
                    RsColor.lookup(tagContent)?.let { ActiveTag("col", it) }
                }

            if (activeTag != null) {
                appendTag(out, activeTag)
                stack.addLast(activeTag)
            } else {
                out.append("<$rawTag>")
            }
            continue
        }

        out.append(ch)
        i++
    }

    if (pendingRestore != null && inheritPreviousTags) appendTag(out, pendingRestore)

    while (stack.isNotEmpty()) out.append(closeTag(stack.removeLast()))

    return if (wrapAt != null) out.toString().wrapLines(wrapAt) else out.toString()
}

private fun appendTag(sb: StringBuilder, tag: ActiveTag) {
    val valuePart = tag.value?.let { "=$it" } ?: ""
    sb.append("<${tag.type}$valuePart>")
}

private fun closeTag(tag: ActiveTag) = when (tag.type) {
    "col" -> "</col>"
    "str" -> "</str>"
    "u" -> "</u>"
    "shad" -> "</shad>"
    else -> ""
}

private fun String.wrapLines(maxLength: Int = 60): String {
    val out = StringBuilder()
    val activeTags = ArrayDeque<String>()
    var visibleCount = 0
    var lastSpaceIndex = -1
    var lastSpaceVisibleCount = 0
    var i = 0

    while (i < length) {
        val ch = this[i]

        if (ch == '<') {
            val closeIndex = indexOf('>', i)
            if (closeIndex == -1) {
                out.append(substring(i))
                break
            }

            val tag = substring(i, closeIndex + 1)
            out.append(tag)

            if (!tag.startsWith("</")) {
                activeTags.addLast(tag)
            } else {
                val closeType = tag.drop(2).takeWhile { it != '=' && it != '>' }
                val idx = activeTags.indexOfLast { it.drop(1).takeWhile { c -> c != '=' && c != '>' } == closeType }
                if (idx != -1) activeTags.removeAt(idx)
            }

            i = closeIndex + 1
            continue
        }

        out.append(ch)
        visibleCount++

        if (ch.isWhitespace()) {
            lastSpaceIndex = out.length - 1
            lastSpaceVisibleCount = visibleCount
        }

        if (visibleCount >= maxLength) {
            val wrapPos = if (lastSpaceIndex != -1) lastSpaceIndex + 1 else out.length

            val tagsToClose = activeTags.toList().reversed()
            val closeTagsStr = tagsToClose.joinToString("") { tag ->
                val type = tag.drop(1).takeWhile { it != '=' && it != '>' }
                "</$type>"
            }
            out.insert(wrapPos, "$closeTagsStr<br>")

            val reopenTagsStr = StringBuilder()
            for (tag in activeTags) {
                val checkLen = tag.length
                val endIndex = (wrapPos + closeTagsStr.length + 4).coerceAtMost(out.length)
                val alreadyHasTag = out.substring(endIndex - checkLen, endIndex).equals(tag, ignoreCase = true)
                if (!alreadyHasTag) reopenTagsStr.append(tag)
            }
            out.insert(wrapPos + closeTagsStr.length + 4, reopenTagsStr.toString())

            visibleCount = if (lastSpaceIndex != -1) visibleCount - lastSpaceVisibleCount else 0
            lastSpaceIndex = -1
            lastSpaceVisibleCount = 0
            i++
        } else {
            i++
        }
    }

    return out.toString()
}
