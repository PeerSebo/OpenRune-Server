package org.rsmod.content.other.commandbrowser

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CommandBrowserScript @Inject constructor(private val access: ProtectedAccessLauncher) : PluginScript() {
    private data class State(val filter: String, var page: Int = 0)

    private val states = WeakHashMap<Player, State>()
    private lateinit var registry: CheatCommandMap

    override fun ScriptContext.startup() {
        registry = cheatCommandMap
        onCommand("commands") {
            desc = "Search commands, descriptions or privileges; ::commands [search]"
            cheat {
                if (player.ui.containsModal(INTERFACE) && !player.isModalButtonProtected) {
                    player.ifClose(eventBus)
                }
                access.launch(player) {
                    open(args.joinToString(" ").take(80))
                }
            }
        }
        onIfModalButton("component.command_browser:previous") { turnPage(-1) }
        onIfModalButton("component.command_browser:next") { turnPage(1) }
        onIfClose(INTERFACE) { states.remove(player) }
    }

    internal fun ProtectedAccess.open(filter: String) {
        ifOpenMainModal(INTERFACE)
        states[player] = State(filter)
        ifSetEvents("component.command_browser:previous", -1..-1, IfEvent.Op1)
        ifSetEvents("component.command_browser:next", -1..-1, IfEvent.Op1)
        render()
    }

    private fun ProtectedAccess.turnPage(delta: Int) {
        if (!player.ui.containsModal(INTERFACE)) return
        val state = states[player] ?: return
        state.page += delta
        render()
    }

    private fun ProtectedAccess.render() {
        val state = states[player] ?: return
        val page = CommandCatalog.page(registry, state.filter, state.page)
        state.page = page.index
        val filter = CommandCatalog.plainText(state.filter)
        val summary = if (filter.isEmpty()) {
            "${page.total} commands - ::commands debug / administrator / (search)"
        } else {
            "${page.total} matches for: $filter"
        }
        ifSetText("component.command_browser:summary", summary)
        for (row in 0 until CommandCatalog.PAGE_SIZE) {
            val entry = page.entries.getOrNull(row)
            val usable = entry?.requiredRights?.let(player.modLevel::isAtLeast) ?: true
            val colour = if (usable) "80ff80" else "ffb080"
            val name = entry?.let { "<col=$colour>::${CommandCatalog.plainText(it.command)}</col>" }.orEmpty()
            ifSetText("component.command_browser:command$row", name)
            ifSetText("component.command_browser:privilege$row", entry?.privilege.orEmpty())
            ifSetText("component.command_browser:description$row", entry?.let { CommandCatalog.plainText(it.description) }.orEmpty())
        }
        ifSetText("component.command_browser:page", "Page ${page.index + 1} of ${page.pages}")
        ifSetText("component.command_browser:previous", if (page.index > 0) "Previous" else "")
        ifSetText("component.command_browser:next", if (page.index + 1 < page.pages) "Next" else "")
    }

    internal companion object {
        const val INTERFACE = "interface.command_browser"
    }
}
