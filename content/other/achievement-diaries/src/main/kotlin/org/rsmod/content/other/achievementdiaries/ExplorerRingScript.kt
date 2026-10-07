package org.rsmod.content.other.achievementdiaries

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import java.time.LocalDate
import java.time.ZoneOffset
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.restoreRunEnergyPercent
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.api.script.onOpWorn5
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal object ExplorerRingCharges {
    const val ResetDay = "varp.diary_lumbridge_ring_reset_day"
    const val Energy = "varbit.lumbridge_energy_restore"
    const val Cabbage = "varbit.lumbridge_cabbage_teleport"
    const val Alchemy = "varbit.lumbridge_free_alchs"

    fun reset(player: Player, day: Int = LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()) {
        if (player.vars[ResetDay] == day) return
        VarPlayerIntMapSetter.set(player, ResetDay, day)
        VarPlayerIntMapSetter.set(player, Energy, 0)
        VarPlayerIntMapSetter.set(player, Alchemy, 0)
        VarPlayerIntMapSetter.set(player, Cabbage, 0)
    }
}

class ExplorerRingScript @Inject constructor(
    private val teleportValidator: PlayerTeleportValidator,
    private val areas: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val ring = DiaryData.easy.ring
        onOpHeld2(ring) { checkCharges() }
        onOpHeld3(ring) { restoreEnergy() }
        onOpHeld4(ring) { openAlchemy() }
        onOpWorn3(ring) { restoreEnergy() }
        onOpWorn4(ring) { openAlchemy() }
        onOpWorn5(ring) { checkCharges() }
        for (tier in DiaryData.tiers.filter { it.difficulty >= 2 }) {
            onOpHeld2(tier.ring) { checkCharges() }
            onOpHeld3(tier.ring) { cabbageTeleport() }
            onOpHeld4(tier.ring) { functions() }
            onOpWorn2(tier.ring) { cabbageTeleport() }
            onOpWorn3(tier.ring) { restoreEnergy() }
            onOpWorn4(tier.ring) { openAlchemy() }
            onOpWorn5(tier.ring) { checkCharges() }
        }
        onPlayerQueueWithArgs<CoordGrid>("queue.diary_explorer_teleport") {
            telejump(it.args, TeleportType.Exempt)
            resetAnim()
        }
        onIfModalButton("component.lumbridge_alchemy:close") {
            ifCloseSub("interface.lumbridge_alchemy")
        }
        onIfModalButton("component.lumbridge_alchemy:low") {
            vars["varbit.lumbridge_alchemy_high"] = 0
        }
        onIfModalButton("component.lumbridge_alchemy:high") {
            mes("Your Explorer's ring cannot cast High Level Alchemy.")
        }
        onIfModalButton("component.lumbridge_alchemy:items") { event ->
            castAlchemy(event.comsub, event.obj?.id)
        }
    }

    private fun ProtectedAccess.availableTier(): DiaryTier? =
        DiaryData.tiers.lastOrNull {
            player.vars[it.rewardVar] > 0 && (it.ring in inv || it.ring in worn)
        }

    private suspend fun ProtectedAccess.functions() {
        if (!canUseRing()) return
        when (menu(
            "Explorer's ring functions",
            hotkeys = true,
            choices = listOf("Restore run energy", "Low Level Alchemy", "Cancel"),
        )) {
            0 -> restoreEnergy()
            1 -> openAlchemy()
        }
    }

    private fun ProtectedAccess.cabbageTeleport() {
        if (!canUseRing() || actionDelay > mapClock) return
        val tier = availableTier() ?: return
        if (tier.cabbageUses >= 0 && vars[ExplorerRingCharges.Cabbage] >= tier.cabbageUses) {
            mes("Your ring has no cabbage teleports remaining today.")
            return
        }
        val denial = teleportValidator.validate(player, TeleportType.Standard, areas)
        if (denial != null) {
            mes(denial)
            return
        }
        if (tier.cabbageUses >= 0) {
            vars[ExplorerRingCharges.Cabbage] = vars[ExplorerRingCharges.Cabbage] + 1
        }
        actionDelay = mapClock + 4
        ifClose()
        player.abortRoute()
        anim("seq.teleport_cabbage_human")
        spotanim("spotanim.teleport_cabbage")
        soundSynth("synth.TELEPORT_ALL")
        clearQueue("queue.diary_explorer_teleport")
        queue("queue.diary_explorer_teleport", 4, tier.cabbageDestination)
    }

    private fun ProtectedAccess.canUseRing(): Boolean {
        if (availableTier() == null) {
            mes("You need your Explorer's ring to do that.")
            return false
        }
        ExplorerRingCharges.reset(player)
        return true
    }

    private fun ProtectedAccess.checkCharges() {
        if (!canUseRing()) return
        val tier = availableTier() ?: return
        val energy = (tier.energyUses - vars[ExplorerRingCharges.Energy]).coerceAtLeast(0)
        val alchemy = (tier.alchemyUses - vars[ExplorerRingCharges.Alchemy]).coerceAtLeast(0)
        mes("Your ring has $energy energy restores and $alchemy alchemy charges remaining today.")
        if (tier.cabbageUses < 0) {
            mes("You have unlimited cabbage teleports.")
        } else if (tier.cabbageUses > 0) {
            val teleports = (tier.cabbageUses - vars[ExplorerRingCharges.Cabbage]).coerceAtLeast(0)
            mes("You have $teleports cabbage teleports remaining today.")
        }
    }

    private fun ProtectedAccess.restoreEnergy() {
        if (!canUseRing()) return
        val tier = availableTier() ?: return
        if (vars[ExplorerRingCharges.Energy] >= tier.energyUses) {
            mes("Your ring has no energy restores remaining today.")
            return
        }
        if (player.runEnergy >= constants.run_max_energy) {
            mes("Your run energy is already full.")
            return
        }
        player.restoreRunEnergyPercent(tier.energyPercent)
        vars[ExplorerRingCharges.Energy] = vars[ExplorerRingCharges.Energy] + 1
        mes("You use your Explorer's ring to restore your run energy.")
    }

    private fun ProtectedAccess.openAlchemy() {
        if (!canUseRing()) return
        vars["varbit.lumbridge_alchemy_high"] = 0
        ifOpenSide("interface.lumbridge_alchemy")
        ifSetEvents("component.lumbridge_alchemy:items", 0..27, IfEvent.Op1)
    }

    private fun ProtectedAccess.castAlchemy(slot: Int, itemId: Int?) {
        if (!canUseRing() || slot !in inv.indices) return
        if (vars[ExplorerRingCharges.Alchemy] >= (availableTier() ?: return).alchemyUses) {
            mes("Your ring has no alchemy charges remaining today.")
            return
        }
        if (player.stat("stat.magic") < 21) {
            mes("You need a Magic level of 21 to cast Low Level Alchemy.")
            return
        }
        if (actionDelay > mapClock) return
        val held = inv[slot] ?: return
        if (held.id != itemId) return
        val item = checkNotNull(ServerCacheManager.getItem(held.id))
        if (item.param(params.no_alchemy) != 0) {
            mes("You cannot cast alchemy on that.")
            return
        }
        val transaction = player.invTransaction(inv) {
            val inventory = select(inv)
            delete {
                from = inventory
                obj = held.id
                strictCount = 1
                strictSlot = slot
            }
            if (item.lowAlch > 0) {
                insert {
                    into = inventory
                    obj = "obj.coins".asRSCM()
                    strictCount = item.lowAlch
                }
            }
        }
        if (!transaction.success) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        vars[ExplorerRingCharges.Alchemy] = vars[ExplorerRingCharges.Alchemy] + 1
        actionDelay = mapClock + 3
        anim("seq.human_castlowlvlalchemy")
        spotanim("spotanim.lowlvlalchemy_casting", height = 92)
        soundSynth("synth.LOW_ALCHEMY", delay = 7)
    }
}
