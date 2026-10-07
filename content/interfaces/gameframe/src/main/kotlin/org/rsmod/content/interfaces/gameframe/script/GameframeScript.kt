package org.rsmod.content.interfaces.gameframe.script

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.NonInterruptingComponents
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifOpenTop
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.script.advanced.onIfMoveSub
import org.rsmod.api.script.advanced.onIfMoveTop
import org.rsmod.api.script.onPlayerInit
import org.rsmod.api.script.onPlayerSoftQueueWithArgs
import org.rsmod.content.interfaces.gameframe.Gameframe
import org.rsmod.content.interfaces.gameframe.GameframeLoader
import org.rsmod.content.interfaces.gameframe.GameframeMove
import org.rsmod.content.interfaces.gameframe.moveGameframe
import org.rsmod.content.interfaces.gameframe.openGameframe
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

var Player.gameframeTopLevel by intVarBit("varbit.gameframe_toplevel")
private var Player.stoneArrangements by boolVarBit("varbit.resizable_stone_arrangement")
lateinit var gameframes: Map<Int, Gameframe>

class GameframeScript @Inject internal constructor(private val eventBus: EventBus, private val loader: GameframeLoader) :
    PluginScript() {

    private lateinit var moveEvents: List<MoveEvent>
    private lateinit var default: Gameframe
    private var Player.orbsMinimized by boolVarBit("varbit.minimap_toggle")

    override fun ScriptContext.startup() {
        loadAll()

        // Side-stone clicks are client tab selection; do not launch an action that cancels ongoing content.
        for (toplevel in listOf("toplevel", "toplevel_osrs_stretch", "toplevel_pre_eoc")) {
            for (stone in 0..13) {
                NonInterruptingComponents.register("component.$toplevel:stone$stone")
            }
        }

        onPlayerInit { player.openLoginGameframe() }

        for ((topLevel, gameframe) in gameframes) {
            val type = ServerCacheManager.getInterface(topLevel) ?: error("Unable to get Interface")
            onIfMoveTop(type) { player.queueGameframeMove(gameframe) }
        }

        for ((target, event) in moveEvents) {
            onIfMoveSub(target) { player.moveSetEvents(event) }
        }
        onIfMoveSub("component.toplevel_osrs_stretch:xp_drops") { player.moveXpDrops() }

        onIfMoveSub("component.toplevel_osrs_stretch:tli_listener") { player.moveEhcListener() }

        onPlayerSoftQueueWithArgs("queue.client_mode") { player.changeGameframe(args) }
        onPlayerSoftQueueWithArgs("queue.fullscreen_map") { player.changeGameframe(args) }
    }

    private fun Player.openLoginGameframe() {
        val gameframe = gameframes[gameframeTopLevel]
        if (gameframe != null && gameframe.resizable == ui.frameResizable) {
            ifOpenTop(gameframe.topLevel)
            openGameframe(gameframe, eventBus)
            return
        }
        val fallback = selectFallback(ui.frameResizable, stoneArrangements) ?: default
        ui.frameResizable = fallback.resizable
        gameframeTopLevel = fallback.topLevel.asRSCM(RSCMType.INTERFACE)
        stoneArrangements = fallback.stoneArrangement
        ifOpenTop(fallback.topLevel)
        openGameframe(fallback, eventBus)
    }

    public fun Player.queueGameframeMove(gameframe: Gameframe) {
        val settingsClientMode = ui.frameResizable != gameframe.resizable
        if (settingsClientMode) {
            runClientScript("clientscript.[clientscript,settings_client_mode]", gameframe.clientMode)
        }
        val previous = gameframes.getValue(gameframeTopLevel)
        gameframeTopLevel = gameframe.topLevel.asRSCM(RSCMType.INTERFACE)
        ui.frameResizable = gameframe.resizable

        val queueDelay = if (settingsClientMode) 2 else 1
        val gameframeMove = resolveGameframeMove(from = previous, dest = gameframe)
        softQueue("queue.client_mode", queueDelay, gameframeMove)
    }

    private fun Player.moveSetEvents(component: String) {
        ifSetEvents(component, -1..-1, IfEvent.Op1)
    }

    private fun Player.moveXpDrops() {
        ifOpenOverlay("interface.orbs", "component.toplevel_osrs_stretch:orbs", eventBus)
    }

    private fun Player.moveEhcListener() {
        ClientScripts.settingsInterfaceScaling(this, 0)
        ClientScripts.buffBarLayoutRedraw(this)
    }

    companion object {
        fun Player.resolveGameframeMove(from: Gameframe, dest: Gameframe): GameframeMove {
            val intermediate = resolveIntermediate(from, dest)
            return GameframeMove(from = from, dest = dest, intermediate = intermediate)
        }

        // Preserve repeated gameframe writes: fixed-to-resizable transitions depend on their ordering.
        private fun Player.resolveIntermediate(from: Gameframe, dest: Gameframe): Gameframe? {
            val requiresIntermediate =
                dest.resizable && !from.resizable && stoneArrangements != dest.stoneArrangement
            if (!requiresIntermediate) {
                return null
            }
            return gameframes.values.first { it.hasFlags(resizable = true, stoneArrangements) }
        }

        private fun Gameframe.hasFlags(resizable: Boolean, stoneArrangements: Boolean): Boolean {
            return this.resizable == resizable && this.stoneArrangement == stoneArrangements
        }
    }

    public fun Player.changeGameframe(move: GameframeMove) {
        val (from, dest, intermediate) = move
        if (dest.resizable) {
            stoneArrangements = dest.stoneArrangement
        }
        resyncVar("varp.chat_filter_assist")
        resyncVar("varp.settings_tracking")

        val sameGameframe = from.topLevel == dest.topLevel
        if (!sameGameframe) {
            if (intermediate != null) {
                moveGameframe(from, intermediate, eventBus)
                moveGameframe(intermediate, dest, eventBus)
            } else {
                moveGameframe(from, dest, eventBus)
            }
            // TODO: Verify how settings_interface_scaling and buff_bar_layout_redraw are scheduled one cycle later.
        }

        this.ifOpenOverlay(if (orbsMinimized && dest.resizable) "interface.orbs_nomap" else "interface.orbs", "component.toplevel_osrs_stretch:orbs", eventBus)
        Cinematic.syncMinimapState(this)
    }

    private fun selectFallback(resizable: Boolean, stoneArrangements: Boolean): Gameframe? {
        val priority = gameframes.values.firstOrNull { it.hasFlags(resizable, stoneArrangements) }
        if (priority != null) {
            return priority
        }
        return gameframes.values.firstOrNull { it.resizable == resizable }
    }

    private fun loadAll() {
        gameframes = loader.loadGameframes()
        moveEvents = loader.loadMoveEvents().mapMoveEvents()
        default = selectDefault(gameframes.values)
    }

    private fun Map<String, String>.mapMoveEvents(): List<MoveEvent> {
        return map { MoveEvent(it.key, it.value) }
    }

    private fun selectDefault(from: Iterable<Gameframe>): Gameframe {
        return from.single(Gameframe::isDefault)
    }

    private data class MoveEvent(val target: String, val event: String)
}
