@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.HunterCatchEvent
import org.rsmod.content.skills.hunter.HunterRumours
import org.rsmod.content.skills.hunter.configs.RumourMaster
import org.rsmod.content.skills.hunter.migrateHunterGuildState
import org.rsmod.content.skills.hunter.rumoursBackToBack
import org.rsmod.content.skills.hunter.rumoursUnlocked
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class HunterRumoursScript @Inject constructor(
    private val random: GameRandom, private val objects: ObjRepository, private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { player.migrateHunterGuildState() }
        onEvent<HunterCatchEvent> {
            HunterRumours.caught(player, prey, random) { item ->
                objects.add(item, player.coords, player.lootDropDuration ?: org.rsmod.api.config.constants.lootdrop_duration, receiver = player)
            }
        }
        for (master in RumourMaster.entries) onOpNpc1(master.npc) { talkMaster(it.npc, master) }
        onOpNpc1("npc.hg_verity") { talkVerity(it.npc) }
    }

    private suspend fun ProtectedAccess.talkMaster(npc: Npc, master: RumourMaster) {
        if (!HunterRumours.canUse(player, master)) {
            mes("Speak to Verity first. This hunter requires ${master.level} Hunter${if (master == RumourMaster.WOLF) " and At First Light" else ""}.")
            return
        }
        var choice = 4
        startDialogue(npc) {
            val assigned = HunterRumours.assigned(player, master)
            chatNpcNoTurn(happy, assigned?.let { "Your rumour concerns ${it.displayName}. Bring me its rare creature part." } ?: "Would you like to investigate a rumour?")
            choice = choice4("Investigate this hunter's rumour.", 1, "Hand in my rare creature part.", 2,
                if (master == RumourMaster.GILMAN) "Reset all my assignments and blocks." else "Check my progress.", 3, "Goodbye.", 4)
        }
        when (choice) {
            1 -> {
                val prey = HunterRumours.assign(player, master, random)
                mes(prey?.let { "Your active rumour is ${it.displayName}. Find its rare creature part." } ?: "No available rumours meet your current requirements.")
            }
            2 -> if (!HunterRumours.complete(player, master, xpMods)) mes("Bring this hunter the rare part found while investigating their rumour.")
            3 -> if (master == RumourMaster.GILMAN) {
                var confirmed = false
                startDialogue(npc) { confirmed = choice2("Reset every assignment and block.", true, "Keep my assignments.", false) }
                if (confirmed) {
                    if (HunterRumours.reset(player, random)) mes("Your assignments and blocks have been cleared. Complete Gilman's new ${HunterRumours.currentName(player)} rumour before returning to another hunter.")
                    else mes("You must have an inactive assignment from Gilman before asking him to reset all rumours.")
                }
            } else mes("Successful catches for this rumour: ${HunterRumours.count(player, master)}. Rumours completed: ${HunterRumours.completed(player)}.")
        }
    }

    private suspend fun ProtectedAccess.talkVerity(npc: Npc) {
        if (player.statMap.getBaseLevel("stat.hunter").toInt() < 46) { mes("You need 46 Hunter to investigate guild rumours."); return }
        var option = 3
        startDialogue(npc) {
            chatNpcNoTurn(happy, "The guild hunters need rare creature parts to investigate rumours. Speak to a hunter to begin, then return their requested part for a reward.")
            option = choice3("I'd like to investigate rumours.", 1, "Toggle back-to-back rumours.", 2, "Goodbye.", 3)
        }
        if (option == 1) { player.rumoursUnlocked = true; mes("You can now request rumours from the guild hunters.") }
        if (option == 2) {
            val enabled = !player.rumoursBackToBack
            player.rumoursBackToBack = enabled
            mes("Back-to-back rumours are now ${if (enabled) "enabled" else "disabled"}.")
        }
    }
}
