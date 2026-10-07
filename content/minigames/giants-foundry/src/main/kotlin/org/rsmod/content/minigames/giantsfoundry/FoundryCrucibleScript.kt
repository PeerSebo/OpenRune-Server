package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.locAnimSpecific
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.*
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMath
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsWorkshop
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.content.skills.smithing.util.SmithingUtils
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FoundryCrucibleScript @Inject constructor(
    private val game: FoundryGame,
    private val queues: WorldQueueList,
    private val workshop: SleepingGiantsWorkshop,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogout {
            if (player.foundryPourPending) player.foundryPourPending = false
        }
        onPlayerCoordsChanged {
            if (player.foundryPourPending && !FoundryGame.isInside(player.coords) &&
                !workshop.contains(player)
            ) player.foundryPourPending = false
        }
        val crucibles = listOf(
            "loc.giants_foundry_crucible_empty",
            "loc.giants_foundry_crucible_partial",
            "loc.giants_foundry_crucible_full",
        )
        for (crucible in crucibles) {
            onOpLoc1(crucible) {
                if (FoundryGame.metalCounts(player).sum() == FoundryMath.CRUCIBLE_CAPACITY) pour(it.loc)
                else fill(it.loc)
            }
            onOpLoc2(crucible) {
                val contents = FoundryData.metals.mapNotNull { metal ->
                    player.vars[metal.countVarbit].takeIf { it > 0 }?.let { "$it ${metal.key}" }
                }
                if (contents.isEmpty()) mes("The crucible is empty.")
                else mes("The crucible contains ${contents.joinToString(", ")} bars.")
            }
            onOpLoc3(crucible) {
                if (player.foundryPourPending) {
                    mes("Wait until the crucible has finished pouring.")
                    return@onOpLoc3
                }
                if (FoundryGame.metalCounts(player).sum() == 0) mes("The crucible is already empty.")
                else if (choice2("Empty the crucible and lose its contents", true, "Cancel", false)) {
                    for (metal in FoundryData.metals) {
                        VarPlayerIntMapSetter.set(player, metal.countVarbit, 0)
                    }
                    VarPlayerIntMapSetter.set(player, FoundryGame.CRUCIBLE_STATE, 0)
                    VarPlayerIntMapSetter.set(player, FoundryGame.ALLOY, 0)
                    mes("You empty the crucible.")
                }
            }
            for (item in FoundryData.materials.keys) {
                onOpLocU(crucible, RSCM.getReverseMapping(RSCMType.OBJ, item)) {
                    selectItemAmount(it.loc, item)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.fill(loc: BoundLocInfo) {
        val limit = FoundryData.metals.maxOf { inv.physicalCount(it.bar) }.coerceAtLeast(1)
        openSkillMulti(SkillMultiConfig(
            actionType = SkillingActionType.SMELT,
            verb = "add",
            titleOverride = "What metal would you like to add?",
            entries = FoundryData.metals.map { SkillMultiEntry(it.bar) },
            maxCountProvider = { _, _ -> limit },
        )) { selection ->
            ifClose()
            deposit(loc, selection.entry.item.id, selection.amount)
        }
    }

    private suspend fun ProtectedAccess.selectItemAmount(loc: BoundLocInfo, item: Int) {
        val type = checkNotNull(ServerCacheManager.getItem(item))
        val count = inv.physicalCount(type.internalName)
        if (count <= 0) return
        val last = player.foundryLastDepositCount.takeIf { it in 2..count && it != 5 }
        val title = "How many would you like to add?"
        val selected = when {
            count == 1 -> 1
            count >= 5 && last != null -> choice5(
                "1", 1, "5", 5, "$last", last, "X", -1, "All", count, title = title,
            )
            count >= 5 -> choice4("1", 1, "5", 5, "X", -1, "All", count, title = title)
            last != null -> choice4("1", 1, "$last", last, "X", -1, "All", count, title = title)
            else -> choice3("1", 1, "X", -1, "All", count, title = title)
        }
        val requested = if (selected == -1) countDialog(title).also {
            if (it > 0) player.foundryLastDepositCount = it
        } else selected
        ifClose()
        deposit(loc, item, requested)
    }

    private suspend fun ProtectedAccess.deposit(loc: BoundLocInfo, item: Int, requested: Int) {
        arriveDelay()
        val material = FoundryData.materials[item] ?: return
        val type = checkNotNull(ServerCacheManager.getItem(item))
        if (inv.physicalCount(type.internalName) == 0) {
            mesbox("You have no ${itemName(type.name, 2)} to add to the crucible.")
            return
        }
        if (player.smithingLvl < material.metal.level) {
            mes("You need a Smithing level of ${material.metal.level} to work with this metal.")
            return
        }
        val room = FoundryMath.CRUCIBLE_CAPACITY - FoundryGame.metalCounts(player).sum()
        if (room <= 0) {
            mes("The crucible is full. You can now pour the molten metal into the mould jig.")
            return
        }
        val count = minOf(inv.physicalCount(type.internalName), requested)
        if (count <= 0) return
        val waste = count.toLong() * material.bars - room
        if (waste > 0 &&
            !confirmObjbox(type.internalName,
                "Adding these items will waste ${barsWorth(waste)} of ${material.metal.key}. " +
                    "Are you sure you want to add them?")
        ) return
        ifClose()
        faceLoc(loc)
        val added: Int
        val consumed: Int
        anim("seq.human_elem2_lever_3way_right")
        try {
            awaitFoundryCycles(3)
            val before = inv.physicalCount(type.internalName)
            added = game.deposit(player, item, count, allowWaste = waste > 0)
            consumed = before - inv.physicalCount(type.internalName)
        } finally {
            resetAnim()
        }
        if (added == 0) return
        val bar = checkNotNull(ServerCacheManager.getItem(material.metal.bar.asRSCM()))
        val message = "You add $consumed ${itemName(type.name, consumed)} to the crucible"
        if (type.internalName == material.metal.bar) {
            objbox(type.internalName, "$message.")
        } else {
            doubleobjbox(type.internalName, material.metal.bar,
                "$message, worth $added ${itemName(bar.name, added)}.")
        }
        val remaining = FoundryMath.CRUCIBLE_CAPACITY - FoundryGame.metalCounts(player).sum()
        if (remaining == 0) {
            mes("The crucible is full. You can now pour the molten metal into the mould jig.")
        } else {
            val amount = if (remaining == 1) "bar's worth" else barsWorth(remaining.toLong())
            mes("You need to add another $amount of metal to the crucible.")
        }
    }

    private suspend fun ProtectedAccess.pour(loc: BoundLocInfo) {
        arriveDelay()
        if (player.foundryPourPending) {
            mes("The crucible is already pouring.")
            return
        }
        if (!FoundryGame.hasCommission(player)) {
            mes("You need a commission from Kovac before casting a sword.")
            return
        }
        if (player.foundryMouldState != 1 || player.foundryStartQuality > 0) {
            mes("You need to set up an empty mould jig first.")
            return
        }
        if (!game.canCast(player)) {
            mes("Choose a forte, blade and tip you can use at the mould jig before pouring.")
            return
        }
        faceLoc(loc)
        anim("seq.human_push")
        soundSynth("synth.SG_CRUCIBLE_POUR_START")
        player.locAnimSpecific(loc, "seq.giants_foundry_crucible_pour01")
        val owner = player
        val rng = random
        val origin = if (workshop.contains(owner)) workshop.map(owner, SleepingGiantsWorkshop.ENTRANCE) else null
        val generation = ++owner.foundryPourGeneration
        owner.foundryPourPending = true
        queues.addDeferred(POUR_CYCLES) { finishPour(owner, loc, origin, generation, rng) }
    }

    private fun finishPour(player: Player, loc: BoundLocInfo, origin: CoordGrid?, generation: Int, random: GameRandom) {
        if (player.foundryPourGeneration != generation) return
        val pending = player.foundryPourPending
        player.foundryPourPending = false
        if (!player.canProcess) return
        val sameWorkshop = if (origin == null) FoundryGame.isInside(player.coords)
            else workshop.contains(player) && workshop.map(player, SleepingGiantsWorkshop.ENTRANCE) == origin
        if (pending && sameWorkshop && game.cast(player, random)) {
            player.soundSynth("synth.SG_CRUCIBLE_POUR_END")
        } else {
            player.locAnimSpecific(loc, "seq.giants_foundry_idle")
        }
    }

    private fun itemName(name: String, count: Int): String =
        SmithingUtils.pluralize(name, count).replaceFirstChar { it.titlecase() }

    private fun barsWorth(count: Long): String = if (count == 1L) "1 bar's worth" else "$count bars' worth"

    private companion object {
        const val POUR_CYCLES = 6
    }
}
