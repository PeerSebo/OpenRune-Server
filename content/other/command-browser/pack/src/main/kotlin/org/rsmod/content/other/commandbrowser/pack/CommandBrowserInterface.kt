package org.rsmod.content.other.commandbrowser.pack

import dev.openrune.cache.filestore.definition.InterfaceType
import dev.openrune.cache.tools.iftype.dsl.buildInterface
import dev.openrune.cache.tools.iftype.dsl.impl.FontType
import dev.openrune.cache.tools.iftype.dsl.impl.layer
import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.pack.PluginPack

private const val WIDTH = 512
private const val HEIGHT = 334
private const val INSET = 14
private const val TITLE_HEIGHT = 36
private const val BOTTOM_INSET = 12
private const val CONTENT_WIDTH = WIDTH - INSET * 2
private const val CONTENT_HEIGHT = HEIGHT - TITLE_HEIGHT - BOTTOM_INSET
private const val TEXT_HEIGHT = 16
private const val HEADING_Y = TEXT_HEIGHT + 2
private const val ROWS_Y = HEADING_Y + TEXT_HEIGHT + 4
private const val ROW_HEIGHT = 36
private const val ROW_COUNT = 6
private const val PRIVILEGE_WIDTH = 160
private const val COLUMN_GAP = 12
private const val COMMAND_WIDTH = CONTENT_WIDTH - PRIVILEGE_WIDTH - COLUMN_GAP
private const val PRIVILEGE_X = COMMAND_WIDTH + COLUMN_GAP
private const val DESCRIPTION_INSET = 8
private const val DESCRIPTION_WIDTH = CONTENT_WIDTH - DESCRIPTION_INSET
private const val DESCRIPTION_HEIGHT = ROW_HEIGHT - TEXT_HEIGHT - 2
private const val NAVIGATION_HEIGHT = 22
private const val NAVIGATION_Y = CONTENT_HEIGHT - NAVIGATION_HEIGHT - 4
private const val NAVIGATION_WIDTH = 116
private const val NAVIGATION_GAP = 20
private const val PAGE_X = NAVIGATION_WIDTH + NAVIGATION_GAP
private const val NEXT_X = CONTENT_WIDTH - NAVIGATION_WIDTH
private const val PAGE_WIDTH = NEXT_X - NAVIGATION_GAP - PAGE_X

class CommandBrowserPluginPack : PluginPack() {
    override fun interfaces(): List<InterfaceType> {
        val id = ConstantProvider.getMapping("interface.command_browser")
        // Stay within the client interface capacity of 1100 groups; expansion requires a coordinated client restart.
        require(id in 0 until 1100) {
            "Command browser interface $id exceeds the existing client's 1100 widget groups"
        }
        return listOf(buildCommandBrowserInterface())
    }
}

internal fun buildCommandBrowserInterface() = buildInterface("interface.command_browser", WIDTH, HEIGHT) {
    onLoadListener {
        val id = ConstantProvider.getMapping("interface.command_browser")
        arrayOf(ConstantProvider.getMapping("clientscript.command_browser_init"), (id shl 16) or 1)
    }
    layer("border") {
        position { 0 to 0 }
        size { WIDTH to HEIGHT }
        noClickThrough { true }
    }
    layer("content") {
        position { INSET to TITLE_HEIGHT }
        size { CONTENT_WIDTH to CONTENT_HEIGHT }
        text("summary") {
            position { 0 to 0 }; size { CONTENT_WIDTH to TEXT_HEIGHT }
            font { FontType.FONT_REGULAR }; color(0xff981f); textShadowed { true }
            xAllignment { 1 }; yAllignment { 1 }
        }
        text("command_heading") {
            position { 0 to HEADING_Y }; size { COMMAND_WIDTH to TEXT_HEIGHT }; display { "Command" }
            font { FontType.FONT_BOLD }; color(0xff981f); textShadowed { true }
        }
        text("privilege_heading") {
            position { PRIVILEGE_X to HEADING_Y }; size { PRIVILEGE_WIDTH to TEXT_HEIGHT }; display { "Required privilege" }
            font { FontType.FONT_BOLD }; color(0xff981f); textShadowed { true }
        }
        for (row in 0 until ROW_COUNT) {
            val y = ROWS_Y + row * ROW_HEIGHT
            text("command$row") {
                position { 0 to y }; size { COMMAND_WIDTH to TEXT_HEIGHT }
                font { FontType.FONT_BOLD }; color(0xffffff); textShadowed { true }
            }
            text("privilege$row") {
                position { PRIVILEGE_X to y }; size { PRIVILEGE_WIDTH to TEXT_HEIGHT }
                font { FontType.FONT_REGULAR }; color(0xffffff); textShadowed { true }
            }
            text("description$row") {
                position { DESCRIPTION_INSET to (y + TEXT_HEIGHT) }; size { DESCRIPTION_WIDTH to DESCRIPTION_HEIGHT }
                font { FontType.FONT_REGULAR }; color(0xc0c0c0); textShadowed { true }
            }
        }
        text("previous") {
            position { 0 to NAVIGATION_Y }; size { NAVIGATION_WIDTH to NAVIGATION_HEIGHT }; display { "Previous" }
            font { FontType.FONT_BOLD }; color(0xff981f); textShadowed { true }
            xAllignment { 1 }; yAllignment { 1 }; addOption("Previous page")
            events = IfEvent.DeprecatedOp1.bitmask.toInt()
        }
        text("page") {
            position { PAGE_X to NAVIGATION_Y }; size { PAGE_WIDTH to NAVIGATION_HEIGHT }; display { "Page 1 of 1" }
            font { FontType.FONT_REGULAR }; color(0xffffff); textShadowed { true }
            xAllignment { 1 }; yAllignment { 1 }
        }
        text("next") {
            position { NEXT_X to NAVIGATION_Y }; size { NAVIGATION_WIDTH to NAVIGATION_HEIGHT }; display { "Next" }
            font { FontType.FONT_BOLD }; color(0xff981f); textShadowed { true }
            xAllignment { 1 }; yAllignment { 1 }; addOption("Next page")
            events = IfEvent.DeprecatedOp1.bitmask.toInt()
        }
    }
}
