package dev.fatter.gpp.manager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun plainTextStripsHtmlTagsAndDecodesEntities() {
        val raw = "<p>Fixed <b>crash</b> &amp; login.</p><ul><li>One</li><li>Two</li></ul>"

        val text = ReleaseNotes.toPlainText(raw)

        assertFalse(text.contains("<"))
        assertFalse(text.contains("&amp;"))
        assertTrue(text.contains("Fixed crash & login."))
        assertTrue(text.contains("• One"))
        assertTrue(text.contains("• Two"))
    }

    @Test
    fun markdownKeepsFormattingButStripsHtml() {
        val raw = "<details><summary>Notes</summary>**Bold** and [link](https://x.y)</details>"

        val text = ReleaseNotes.toMarkdown(raw)

        assertFalse(text.contains("<details>"))
        assertFalse(text.contains("</summary>"))
        assertTrue(text.contains("**Bold**"))
        assertTrue(text.contains("[link](https://x.y)"))
    }

    @Test
    fun plainTextConvertsMarkdownLinksAndEmphasis() {
        val raw = "## Changes\n\n- **Fix** [issue](https://example.com/a)\n- `code`"

        val text = ReleaseNotes.toPlainText(raw)

        assertFalse(text.contains("##"))
        assertFalse(text.contains("**"))
        assertFalse(text.contains("]("))
        assertTrue(text.contains("• Fix issue"))
        assertTrue(text.contains("• code"))
    }

    @Test
    fun renamesLegacyBrandingButNotUrls() {
        val raw = "GrindrPlus 5.0 — see https://github.com/R0rt1z2/GrindrPlus"

        val text = ReleaseNotes.toPlainText(raw)

        assertTrue(text.startsWith("Grindr++ 5.0"))
        assertTrue(text.contains("https://github.com/R0rt1z2/GrindrPlus"))
    }

    @Test
    fun collapsesBlankLinesAndTrims() {
        val raw = "\n\nFirst<br><br><br>Second\n\n"

        assertEquals("First\n\nSecond", ReleaseNotes.toPlainText(raw))
    }

    @Test
    fun imagesKeepAltText() {
        val raw = "Before ![screenshot](https://example.com/a.png) after"

        assertEquals("Before screenshot after", ReleaseNotes.toPlainText(raw))
        assertEquals("Before screenshot after", ReleaseNotes.toMarkdown(raw))
    }
}
