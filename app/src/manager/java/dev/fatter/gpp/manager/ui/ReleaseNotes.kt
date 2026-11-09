package dev.fatter.gpp.manager.ui

/**
 * GitHub release notes are GitHub-flavored Markdown and may embed raw HTML
 * (`<br>`, `<details>`, entities). Compose renders those tags literally, so
 * normalize the body once at parse time.
 *
 * - [toMarkdown] keeps Markdown syntax for the Home card renderer but strips
 *   HTML, decodes entities, and turns images into their alt text.
 * - [toPlainText] additionally removes Markdown syntax for the compact News
 *   preview.
 *
 * Both also rename legacy upstream branding to the product name without
 * touching URLs (e.g. `github.com/R0rt1z2/GrindrPlus`).
 */
object ReleaseNotes {
    private val htmlBreak = Regex("(?i)<br\\s*/?>")
    private val htmlListItem = Regex("(?i)<li[^>]*>")
    private val htmlBlockEnd = Regex(
        "(?i)</(p|div|li|tr|h[1-6]|details|summary|ul|ol|blockquote|table|section|article)>"
    )
    private val htmlTag = Regex("<[^>]+>")
    private val image = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val link = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val heading = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val bullet = Regex("(?m)^\\s*[-*+]\\s+")
    private val rule = Regex("(?m)^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")
    private val emphasis = Regex("(\\*\\*|__|~~|\\*|_|`)")
    private val entity = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
    private val legacyName = Regex("(?i)(?<![/\\w])(?:grindr\\s*plus|grindplus)\\b")
    private val blankLines = Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+")
    private val trailingSpaces = Regex("[ \\t]+(?=\\n)")

    fun toMarkdown(raw: String): String = normalize(raw, keepMarkdown = true)

    fun toPlainText(raw: String): String = normalize(raw, keepMarkdown = false)

    private fun normalize(raw: String, keepMarkdown: Boolean): String {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')

        // Block-level HTML -> line breaks / list markers before stripping tags.
        text = htmlBreak.replace(text, "\n")
        text = htmlListItem.replace(text, "\n- ")
        text = htmlBlockEnd.replace(text, "\n")
        text = htmlTag.replace(text, "")
        text = decodeEntities(text)

        // Images are never fetched in the release card; keep the alt text.
        text = image.replace(text, "$1")

        if (!keepMarkdown) {
            text = link.replace(text, "$1")
            text = heading.replace(text, "")
            text = bullet.replace(text, "• ")
            text = rule.replace(text, "")
            text = emphasis.replace(text, "")
        }

        text = legacyName.replace(text, "Grindr++")
        text = trailingSpaces.replace(text, "")
        text = blankLines.replace(text, "\n\n")
        return text.trim()
    }

    private fun decodeEntities(text: String): String = entity.replace(text) { match ->
        when (val name = match.groupValues[1].lowercase()) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            "mdash" -> "—"
            "ndash" -> "–"
            "hellip" -> "…"
            "lsquo" -> "‘"
            "rsquo" -> "’"
            "ldquo" -> "“"
            "rdquo" -> "”"
            else -> when {
                name.startsWith("#x") ->
                    name.drop(2).toIntOrNull(16)?.let(::codePoint) ?: match.value
                name.startsWith("#") ->
                    name.drop(1).toIntOrNull()?.let(::codePoint) ?: match.value
                else -> match.value
            }
        }
    }

    private fun codePoint(value: Int): String =
        if (value in 0..0x10FFFF) String(Character.toChars(value)) else ""
}
