package org.rsmod.api.utils.format

import RsColor
import RsStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import toRs

class RSStringParserTest {
    @Test
    fun `quest journal highlights preserve their existing output`() {
        assertEquals("", "".toRs())
        assertEquals("A plain journal line.", "A plain journal line.".toRs())
        assertEquals(
            "<col=000080>I should <col=800000>drink</col> <col=000080>from " +
                "<col=800000>Hetty's cauldron</col><col=000080>.</col>",
            "<blue>I should <red>drink</red> from <red>Hetty's cauldron</red>.</blue>".toRs(),
        )
        // QuestJournal.line uses native <str>; its unmatched closing tag is currently consumed.
        assertEquals(
            "<str>I have found the <col=800000>black bead</col>.",
            "<str>I have found the <red>black bead</red>.</str>".toRs(),
        )
    }

    @Test
    fun `named colours custom hex and style lookups retain their accepted inputs`() {
        val colours = mapOf(
            "RED" to "800000", "GREEN" to "00ff00", "BLUE" to "000080",
            "WHITE" to "ffffff", "YELLOW" to "ffff00", "CYAN" to "00ffff",
            "MAGENTA" to "ff00ff", "BLACK" to "000000",
        )
        for ((input, expected) in colours) assertEquals(expected, RsColor.lookup(input), input)
        for (input in listOf("AbC", "AbCd", "AbCdE", "AbCdEf", "AbCdEf0", "AbCdEf01")) {
            assertEquals(input.lowercase(), RsColor.lookup(input), input)
        }
        for (input in listOf("", "ab", "abcdef012", "#fff", " red ", "unknown", "fffffg")) {
            assertEquals(null, RsColor.lookup(input), input)
        }
        assertEquals(RsStyle.STRIKE, RsStyle.fromTag("StRiKe"))
        assertEquals(RsStyle.STRIKE, RsStyle.fromTag("STR\u0130KE"))
        assertEquals(RsStyle.UNDERLINE, RsStyle.fromTag("UNDERLINE"))
        assertEquals(RsStyle.SHADOW, RsStyle.fromTag("sHaD"))
        assertEquals(null, RsStyle.fromTag("str"))
        assertEquals(null, RsStyle.fromTag(" strike "))
    }

    @Test
    fun `plain and coloured styles retain colour fallback and closing tags`() {
        assertEquals(
            "<str>done</str> <u>next</u> <shad>hint</shad>",
            "<strike>done</strike> <underline>next</underline> <shad>hint</shad>".toRs(),
        )
        assertEquals(
            "<str=800000>done</str> <u=abc123>next</u> <shad=unknown>hint</shad>",
            ("<STRIKE=RED>done</strike> <underline=AbC123>next</underline> " +
                "<shad=unknown>hint</shad>").toRs(),
        )
    }

    @Test
    fun `nested tags restore only when inheritance is enabled`() {
        val input = "<red>A<underline>B<blue>C</blue> D</underline> E</red>"
        assertEquals(
            "<col=800000>A<u>B<col=000080>C</col> <u>D</u> <col=800000>E</col>",
            input.toRs(inheritPreviousTags = true),
        )
        assertEquals(
            "<col=800000>A<u>B<col=000080>C</col> D</u> E</col>",
            input.toRs(inheritPreviousTags = false),
        )
        assertEquals(
            "<col=800000>A<col=000080>B</col>  <col=800000></col>",
            "<red>A<blue>B</blue>  ".toRs(),
        )
        assertEquals(
            "<col=800000>A<col=000080>B</col></col>",
            "<red>A<blue>B</blue></red>".toRs(),
        )
    }

    @Test
    fun `tag normalization and unknown markup remain compatible`() {
        val cases = mapOf(
            "< BLUE >blue</BLUE>" to "<col=000080>blue</col>",
            "<str\u0131ke>done</strike>" to "<str>done</str>",
            "<AbC>hex</abc>" to "<col=abc>hex</col>",
            "<AbCdEf01>hex</abcdef01>" to "<col=abcdef01>hex</col>",
            "<Mystery>text</Mystery>" to "<Mystery>text",
            "<strike =red>text</strike>" to "<strike =red>text",
            "<col=ff0000>native</col>" to "<col=ff0000>native",
        )
        for ((input, expected) in cases) assertEquals(expected, input.toRs(), input)
    }

    @Test
    fun `unclosed and malformed markup preserves legacy stack behavior`() {
        val cases = mapOf(
            "before <red" to "before <red",
            "<red>unfinished" to "<col=800000>unfinished</col>",
            "</blue>plain" to "plain",
            "<red>A</mismatch>B" to "<col=800000>A</col>B",
            "<red>A<unknown>B</unknown>C</red>" to "<col=800000>A<unknown>B</col>C",
            "<red>A<>B</red>" to "<col=800000>A<>B</col>",
            "<red>A<<blue>B</red>" to "<col=800000>A<<blue>B</col>",
        )
        for ((input, expected) in cases) assertEquals(expected, input.toRs(), input)
    }

    @Test
    fun `journal width wraps visible text while closing and reopening styles`() {
        assertEquals("x".repeat(63), "x".repeat(63).toRs(wrapAt = 64))
        assertEquals("x".repeat(64) + "<br>", "x".repeat(64).toRs(wrapAt = 64))
        assertEquals(
            "<col=000080><u>" + "word ".repeat(12) + "</u></col><br><col=000080><u>" +
                "word ".repeat(3) + "tail</u></col>",
            ("<blue><underline>" + "word ".repeat(15) + "tail</underline></blue>")
                .toRs(inheritPreviousTags = true, wrapAt = 64),
        )
        assertEquals(
            "<col=000080>I should go and speak to <col=800000>Juliet</col><col=000080>. " +
                "I can find her <col=800000>west</col> <col=000080>of " +
                "</col></col></col></col><br>" +
                "<col=000080><col=000080><col=000080><col=000080>" +
                "<col=800000>Varrock</col><col=000080>.</col>",
            ("<blue>I should go and speak to <red>Juliet</red>. I can find her <red>west</red> " +
                "of <red>Varrock</red>.</blue>").toRs(inheritPreviousTags = true, wrapAt = 64),
        )
    }
}
