package org.rsmod.content.areas.city.draynor.manor

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal data class AvaDevice(
    val output: String,
    val level: Int,
    val coins: Int,
    val upgrade: Int,
    val arrows: Int,
    val arrowCount: Int,
    val quest: String,
)

internal object AvaDevices {
    val all: List<AvaDevice> by lazy {
        DbHelper.table("dbtable.ava_devices").map { row ->
            fun int(name: String) = row.column("dbcol.ava_devices:$name", IntCodec)
            fun item(name: String) = row.column("dbcol.ava_devices:$name", ObjIdCodec)
            AvaDevice(
                RSCM.getReverseMapping(RSCMType.OBJ, item("output")), int("level"),
                int("coins"), item("upgrade"), item("arrows"), int("arrow_count"),
                row.column("dbcol.ava_devices:quest", StringCodec),
            )
        }.sortedBy { it.level }.also { require(it.size == 2) }
    }
}

class Ava : PluginScript() {
    override fun ScriptContext.startup() {
        AvaDevices.all
        for (npc in listOf("npc.anma_assistant", "npc.anma_assistant_multi")) {
            onOpNpc1(npc) { devices(it.npc) }
            onOpNpc4(npc) { devices(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.devices(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "Would you like one of my devices?")
            when (choice3("Buy an attractor.", 1, "Buy or upgrade to an accumulator.", 2, "Never mind.", 3)) {
                1 -> {
                    chatNpc(neutral, "An attractor costs 999 coins.")
                    if (choice2("Yes, please.", 1, "No, thank you.", 2) == 1) buy(AvaDevices.all[0])
                }
                2 -> {
                    chatNpc(neutral, "An accumulator costs 75 steel arrows and either an attractor or 999 coins. You need level 50 Ranged.")
                    if (choice2("Yes, please.", 1, "No, thank you.", 2) == 1) buy(AvaDevices.all[1])
                }
            }
        }
    }

    internal fun ProtectedAccess.buy(device: AvaDevice): Boolean {
        if (!QuestRequirements.hasCompleted(player, device.quest)) {
            mes("You need to complete Animal Magnetism before buying a device.")
            return false
        }
        if (player.statBase("stat.ranged") < device.level) {
            mes("You need a Ranged level of ${device.level} to buy this device.")
            return false
        }
        fun physicalCount(item: Int): Int =
            if (item < 0) 0 else inv.physicalCount(RSCM.getReverseMapping(RSCMType.OBJ, item))
        val upgrade = physicalCount(device.upgrade) > 0
        val cost = if (upgrade) device.upgrade else "obj.coins".asRSCM()
        val count = if (upgrade) 1 else device.coins
        if (physicalCount(cost) < count || physicalCount(device.arrows) < device.arrowCount) {
            mes("You need ${device.coins} coins${if (device.arrowCount > 0) " (or an attractor) and ${device.arrowCount} steel arrows" else ""}.")
            return false
        }
        val result = player.invTransaction(inv) {
            val inventory = select(inv)
            delete {
                from = inventory
                obj = cost
                strictCount = count
            }
            if (device.arrowCount > 0) {
                delete {
                    from = inventory
                    obj = device.arrows
                    strictCount = device.arrowCount
                }
            }
            insert {
                into = inventory
                obj = device.output.asRSCM()
                strictCount = 1
            }
        }
        if (!result.success) {
            mes("You need space in your inventory for the device.")
            return false
        }
        publish(PlayerActionCompleteEvent(player, PlayerAction.BuyDevice, device.output))
        mes("Ava gives you your device.")
        return true
    }
}
