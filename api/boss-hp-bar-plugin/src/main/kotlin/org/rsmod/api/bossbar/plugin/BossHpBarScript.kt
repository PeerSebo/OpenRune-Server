package org.rsmod.api.bossbar.plugin

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.awt.Color
import java.util.IdentityHashMap
import org.rsmod.api.bossbar.BossHpBarMode
import org.rsmod.api.config.refs.params
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstancePlayerJoinUnboundEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveUnboundEvent
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.setColour
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
public class BossHpBarScript @Inject constructor(
    private val instances: InstanceManager,
    private val players: PlayerList,
    private val clock: MapClock,
) : PluginScript() {
    private data class Entry(
        val npc: Npc,
        var lastAttack: Int,
        val instance: InstanceId?,
        var deathCycle: Int? = null,
        var faded: Boolean = false,
    )

    private val tracked = IdentityHashMap<Player, Entry>()
    private var Player.disabled by boolVarBit("varbit.hpbar_hud_boss_disabled")
    private var Player.currentHp by intVarBit("varbit.hpbar_hud_hp")
    private var Player.maximumHp by intVarBit("varbit.hpbar_hud_basehp")
    private var Player.displayedNpc by intVarp("varp.hpbar_hud_npc")
    private var Player.barSize by intVarBit("varbit.hpbar_hud_boss")

    override fun ScriptContext.startup() {
        onEvent<NpcHitEvents.AnyModify> {
            if (!hit.isFromPlayer) return@onEvent
            val source = hit.sourceUid?.let { PlayerUid(it).resolve(players) } ?: return@onEvent
            attacked(source, npc)
        }
        onEvent<NpcHitEvents.AnyImpact> {
            if (!hit.isFromPlayer) return@onEvent
            val source = hit.resolvePlayerSource(players) ?: return@onEvent
            val current = tracked[source]
            if (current == null || current.npc === npc) attacked(source, npc)
        }
        onEvent<InstancePlayerJoinUnboundEvent> {
            instances.npcsForInstance(instanceId).firstOrNull {
                mode(it) == BossHpBarMode.ON_ENTER && available(it)
            }?.let { open(player, it, instanceId) }
        }
        onEvent<InstancePlayerLeaveUnboundEvent> { close(player) }
        onEvent<NpcStateEvents.Delete> { if (npc.hitpoints > 0) closeNpc(npc) }
        onEvent<NpcStateEvents.Respawn> {
            closeNpc(npc)
            if (mode(npc) != BossHpBarMode.ON_ENTER) return@onEvent
            val instance = instances.instanceForNpc(npc) ?: return@onEvent
            for (player in occupants(instance)) open(player, npc, instance)
        }
        onEvent<GameLifecycle.LateCycle> { sync() }
        onPlayerLogin { reset(player) }
        onPlayerLogout { reset(player) }
    }

    public fun onOpen(player: Player, npc: Npc) {
        attacked(player, npc)
    }

    public fun onUpdate(player: Player, npc: Npc) {
        if (tracked[player]?.npc === npc) update(player, npc)
    }

    public fun onRecolour(player: Player, npc: Npc) {
        if (tracked[player]?.npc !== npc || player.disabled) return
        applyColours(player, npc)
    }

    public fun onClose(player: Player, npc: Npc) {
        if (tracked[player]?.npc === npc) close(player)
    }

    private fun attacked(player: Player, npc: Npc) {
        if (!player.isValidTarget() || !available(npc) || mode(npc) == BossHpBarMode.NEVER) return
        val instance = instances.instanceForNpc(npc)
        if (instance == null || mode(npc) == BossHpBarMode.ON_ATTACK) {
            open(player, npc, instance)
        } else {
            for (occupant in occupants(instance)) open(occupant, npc, instance)
        }
    }

    private fun open(player: Player, npc: Npc, instance: InstanceId?) {
        if (player.disabled || !nearby(player, npc)) return
        val previous = tracked[player]
        if (previous?.npc === npc) {
            previous.lastAttack = clock.cycle
            update(player, npc)
            return
        }
        tracked[player] = Entry(npc, clock.cycle, instance)
        update(player, npc)
        player.barSize = 1
        applyColours(player, npc)
        // Native hp_hud_open resets, positions, fades and updates the HUD with nineteen components.
        player.runClientScript("clientscript.[clientscript,hp_hud_open]", openComponents)
    }

    private fun update(player: Player, npc: Npc) {
        if (player.displayedNpc != npc.visType.id) player.displayedNpc = npc.visType.id
        if (player.maximumHp != npc.baseHitpointsLvl) player.maximumHp = npc.baseHitpointsLvl
        if (player.currentHp != npc.hitpoints) player.currentHp = npc.hitpoints
    }

    private fun applyColours(player: Player, npc: Npc) {
        player.setColour("component.hpbar_hud:health_bar_back",
            Color(npc.visType.paramOrNull(params.boss_hp_bar_colour_back) ?: 0x950000))
        player.setColour("component.hpbar_hud:health_bar_sliding",
            Color(npc.visType.paramOrNull(params.boss_hp_bar_colour_sliding) ?: 0x00f500))
        player.setColour("component.hpbar_hud:health_bar_remaining",
            Color(npc.visType.paramOrNull(params.boss_hp_bar_colour_remaining) ?: 0x00c800))
    }

    private fun sync() {
        for ((player, entry) in tracked.entries.toList()) {
            val npc = entry.npc
            if (npc.hitpoints <= 0) {
                syncDeath(player, entry)
                continue
            }
            val expired = entry.instance == null && clock.cycle - entry.lastAttack >= ATTACK_TIMEOUT_TICKS
            val leftInstance = entry.instance != null && instances.sessionForPlayer(player)?.id != entry.instance
            if (!player.isValidTarget() || player.disabled || !available(npc) || !nearby(player, npc) ||
                mode(npc) == BossHpBarMode.NEVER || expired || leftInstance) {
                close(player)
            } else {
                update(player, npc)
            }
        }
    }

    private fun syncDeath(player: Player, entry: Entry) {
        val started = entry.deathCycle ?: clock.cycle.also { entry.deathCycle = it }
        val elapsed = clock.cycle - started
        if (elapsed >= DEATH_FADE_TICKS && !entry.faded) {
            player.runClientScript("clientscript.[clientscript,hp_hud_fade_out]".asRSCM(), openComponents.toIntArray(), 0)
            entry.faded = true
        }
        if (elapsed >= DEATH_CLOSE_TICKS) close(player)
    }
    private fun closeNpc(npc: Npc) {
        for ((player, entry) in tracked.entries.toList()) if (entry.npc === npc) close(player)
    }

    private fun close(player: Player) {
        if (tracked.remove(player) != null) reset(player)
    }

    private fun reset(player: Player) {
        tracked.remove(player)
        player.ifSetHide("component.hpbar_hud:hp", true)
        player.displayedNpc = -1
        player.currentHp = 0
        player.maximumHp = 0
        player.barSize = 0
    }

    private fun occupants(instance: InstanceId): List<Player> {
        val occupants = instances.sessionForId(instance)?.occupants ?: return emptyList()
        return players.filter { it.uuid in occupants }
    }

    private fun mode(npc: Npc): BossHpBarMode =
        BossHpBarMode.fromId(npc.visType.paramOrNull(params.boss_hp_bar_mode) ?: npc.type.paramOrNull(params.boss_hp_bar_mode) ?: 0)

    private fun available(npc: Npc): Boolean = npc.isSlotAssigned && npc.isVisible && npc.hitpoints > 0

    private fun nearby(player: Player, npc: Npc): Boolean =
        player.level == npc.level && player.coords.chebyshevDistance(npc.coords) <= 32

    private val openComponents: List<Int> = listOf(
        "universe", "hpdodgerside", "hpdodger", "hp", "name_area", "hp_bar", "hp_bar_text",
        "health_bar_back", "health_bar_sliding", "health_bar_remaining", "creature_name",
        "outer_border", "name_backing", "inner_border", "health_bar_lower_threshold",
        "health_bar_upper_threshold", "hp_bar_1", "hp_bar_2", "hpcontentside",
    ).map { "component.hpbar_hud:$it".asRSCM() }

    internal companion object {
        internal const val ATTACK_TIMEOUT_TICKS = 10
        private const val DEATH_FADE_TICKS = 10
        private const val DEATH_CLOSE_TICKS = 14
    }
}
