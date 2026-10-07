package org.rsmod.content.minigames.blastfurnace.scripts

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseSmithingLvl
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.minigames.blastfurnace.configs.BlastFurnaceParams
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ForemanScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.blast_furnace_instructor") { startDialogue(it.npc) { talkTo() } }
        onOpNpc3("npc.blast_furnace_instructor") { startDialogue(it.npc) { payMenu() } }

        onPlayerSoftTimer(BlastFurnaceParams.FOREMAN_FEE_TIMER) {
            player.clearSoftTimer(BlastFurnaceParams.FOREMAN_FEE_TIMER)
        }
    }

    private suspend fun Dialogue.talkTo() {
        chatNpc(neutral, "You! Get to work!")
        val opening = choice2("What?", 1, "I'd rather not.", 2, title = "Select an Option")
        if (opening != 1) {
            chatPlayer(neutral, "I'd rather not.")
            return
        }
        chatPlayer(confused, "What?")
        chatNpc(neutral, "You are here to help work the blast furnace, aren't you?")

        if (access.player.foremanPermission || access.player.hasBlastFurnaceAccess()) {
            val choice =
                choice2("Can I use the furnace to smelt ore?", 1, "Not right now.", 2, title = "Select an Option")
            if (choice == 1) {
                chatPlayer(neutral, "Can I use the furnace to smelt ore?")
                chatNpc(neutral, "You've already got my permission to use the furnace, lad.")
            } else {
                chatPlayer(neutral, "Not right now.")
            }
            return
        }

        val choice =
            choice2(
                "Can I use the furnace to smelt ore?",
                1,
                "Not right now.",
                2,
                title = "Select an Option",
            )
        if (choice != 1) {
            chatPlayer(neutral, "Not right now.")
            return
        }
        chatPlayer(neutral, "Can I use the furnace to smelt ore?")
        chatNpc(
            neutral,
            "The dwarves keep the furnace running. Put coins in the coffer to pay their wages. " +
                "I also charge a separate fee if you don't have level 60 Smithing.",
        )

        if (access.player.baseSmithingLvl >= BlastFurnaceParams.FREE_ACCESS_LEVEL) {
            val brag = choice2("I have level 60!", 1, "Fair enough.", 2, title = "Select an Option")
            if (brag != 1) {
                chatPlayer(neutral, "Fair enough.")
                return
            }
            chatPlayer(happy, "I have level 60!")
            chatNpc(happy, "A human has level 60 smithing? How extraordinary!")
            chatNpc(
                happy,
                "Feel free to use the furnace. Remember, you only need half as much coal as " +
                    "with a regular furnace.",
            )
            access.player.foremanPermission = true
            return
        }

        payMenu()
    }

    private suspend fun Dialogue.payMenu() {
        if (BlastFurnaceParams.FOREMAN_FEE_TIMER in access.player.softTimerMap) {
            chatNpc(neutral, "You've already paid for access to the furnace, lad.")
            return
        }

        if (access.player.baseSmithingLvl >= BlastFurnaceParams.FREE_ACCESS_LEVEL) {
            if (!access.player.foremanPermission) {
                chatNpc(happy, "You are an experienced smith, you may use the furnace free of charge!")
                chatNpc(neutral, "Remember, you only need half as much coal as with a regular furnace.")
                access.player.foremanPermission = true
            } else {
                chatNpc(neutral, "You don't need to pay, lad - I've already given you free access.")
            }
            return
        }

        val fee = access.player.foremanFeeGp()
        if (access.inv.count("obj.coins") < fee) {
            chatNpc(neutral, "What is this? The fee to use the furnace is ${fee.formatAmount} coins.")
            chatPlayer(sad, "I don't have enough money on me.")
            return
        }

        chatNpc(
            neutral,
            "The fee to use the furnace is ${fee.formatAmount} coins for ten minutes. Do you " +
                "wish to pay?",
        )
        val confirm = choice2("Pay ${fee.formatAmount} coins.", 1, "Never mind.", 2, title = "Select an Option")
        if (confirm != 1) {
            chatPlayer(neutral, "Never mind.")
            return
        }

        if (access.invDel(access.inv, "obj.coins", fee).failure) {
            return
        }
        access.player.softTimer(
            BlastFurnaceParams.FOREMAN_FEE_TIMER,
            BlastFurnaceParams.FOREMAN_FEE_WINDOW_TICKS,
        )
        chatNpc(
            happy,
            "Okay, you can use the furnace for ten minutes. Remember, you only need half as " +
                "much coal as with a regular furnace.",
        )
    }
}

internal var Player.foremanPermission by boolVarBit(BlastFurnaceParams.FOREMAN_PERMISSION_VARBIT)

internal fun Player.foremanFeeGp(): Int =
    if ("obj.ring_of_charos_unlocked" in worn) {
        BlastFurnaceParams.FOREMAN_FEE_GP_CHAROS
    } else {
        BlastFurnaceParams.FOREMAN_FEE_GP
    }

internal fun Player.hasBlastFurnaceAccess(): Boolean {
    if (baseSmithingLvl >= BlastFurnaceParams.FREE_ACCESS_LEVEL && foremanPermission) {
        return true
    }
    return BlastFurnaceParams.FOREMAN_FEE_TIMER in softTimerMap
}
