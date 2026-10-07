package org.rsmod.content.skills.construction.sawmill

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc4
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SawmillOperatorScript @Inject constructor(
    private val shops: Shops,
    private val npcs: NpcRepository,
    private val objs: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (operator in Operators) {
            onOpNpc1(operator) { talk(it.npc) }
            onApNpc1(operator) { if (isWithinApRange(it.npc, CounterRange)) talk(it.npc) }
            onOpNpc3(operator) { buyPlanks(it.npc) }
            onApNpc3(operator) { if (isWithinApRange(it.npc, CounterRange)) buyPlanks(it.npc) }
            onOpNpc4(operator) { trade(it.npc) }
            onApNpc4(operator) { if (isWithinApRange(it.npc, CounterRange)) trade(it.npc) }
            onEvent<NpcStateEvents.Spawn>(operator.asRSCM()) { faceCounter(npc) }
        }
        onGameStartup {
            for (coords in Facing.keys) npcs.findAll(coords).forEach(::faceCounter)
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) {
            chatNpcNoTurn(neutral, "Do you want me to make some planks for you, or would you like some other Construction supplies?")
            when (choice3("I'd like some planks, please.", 1, "What else are you selling?", 2, "Nothing, thanks.", 3)) {
                1 -> buyPlanks(npc)
                2 -> trade(npc)
            }
        }
    }

    private fun ProtectedAccess.trade(npc: Npc) {
        shops.openConfigured(player, npc, "inv.poh_sawmill_shop")
    }

    internal suspend fun ProtectedAccess.buyPlanks(npc: Npc) {
        val held = SawmillPlanks.recipes.filter { inv.physicalCount(it.logs.internalName) > 0 }
        if (held.isEmpty()) {
            mes("You need to bring unnoted logs and coins to the sawmill operator to make planks.")
            return
        }
        if (held.none { inv.physicalCount(SawmillPlanks.COINS) >= it.coins }) {
            mes("You don't have enough coins to pay for those planks.")
            return
        }
        openSkillMulti(SkillMultiConfig(
            actionType = SkillingActionType.BUY,
            verb = "buy",
            entries = held.map {
                SkillMultiEntry(it.plank.internalName, listOf(
                    Material(it.logs.internalName), Material(SawmillPlanks.COINS, it.coins),
                ))
            },
            maxCountProvider = { inventory, entry ->
                entry.materials.minOf { inventory.physicalCount(it.internal) / it.count }
            },
        )) { selection ->
            val recipe = held.single { it.plank.internalName == selection.entry.internal }
            ifClose()
            if (!npc.isVisible || !isWithinDistance(npc, CounterRange)) {
                mes("You need to stand next to the sawmill operator to make planks.")
                return@openSkillMulti
            }
            if (SawmillPlanks.convert(player, recipe, selection.amount) > 0) {
                objs.add("obj.newsletter_sawdust", coords, SawdustTicks)
            }
        }
    }

    internal fun faceCounter(npc: Npc) {
        if (Operators.none(npc::isType)) return
        Facing[npc.spawnCoords]?.let(npc::respawnDirection)
    }

    internal companion object {
        const val CounterRange = 1
        const val SawdustTicks = 100
        val Operators = listOf("npc.poh_sawmill_opp", "npc.prif_sawmill_operator", "npc.auburn_sawmill_operator")
        val Facing = mapOf(
            CoordGrid(3302, 3492, 0) to Direction.South,
            CoordGrid(1623, 3500, 0) to Direction.East,
            CoordGrid(1396, 3369, 0) to Direction.South,
        )
    }
}
