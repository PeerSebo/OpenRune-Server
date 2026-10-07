package org.rsmod.content.minigames.giantsfoundry.quest

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext

class SleepingGiants @Inject constructor(
    private val service: SleepingGiantsService,
    private val workshop: SleepingGiantsWorkshop,
) : QuestScript(
    SleepingGiantsState.KEY,
    "varp.giants_foundry_main",
    rewards { xp("stat.smithing", 6000.0) },
    ItemRewardDisplay("obj.giants_foundry_preform"),
    questVarbit = SleepingGiantsState.STAGE_VARBIT,
    completionJingle = 154,
    completionJingleLength = 5869,
) {
    override fun ScriptContext.init() {
        check(quest.maxSteps == SleepingGiantsState.COMPLETE)
        onOpNpc1("npc.giants_foundry_kovac_fake_attack") { service.start(this, it.npc) }
        onOpNpc1("npc.giants_foundry_kovac_quest") { service.talk(this, it.npc) }
        for (npc in listOf("npc.giants_foundry_kovac_fake_attack", "npc.giants_foundry_kovac_quest")) {
            onOpNpcU(npc, "obj.hammer") { service.start(this, it.npc) }
            onOpNpcU(npc, "obj.imcando_hammer") { service.start(this, it.npc) }
            onOpNpcU(npc, "obj.imcando_hammer_offhand") { service.start(this, it.npc) }
        }
        for (repair in SleepingGiantsData.repairs) {
            onOpLoc1(repair.broken) { service.repair(this, it.loc, repair) }
            onOpLocU(repair.broken) { service.repair(this, it.loc, repair) }
            onOpLoc1(repair.repaired) { mes("The ${repair.name} is repaired and ready to use.") }
        }
        onPlayerLogin { workshop.leave(player) }
        onPlayerLogout { workshop.leave(player) }
        onPlayerCoordsChanged {
            if (!workshop.contains(player)) workshop.leave(player)
        }
    }

    override fun subTitle() =
        "talking to the <col=800000>Hill Giant</col> by the cave east of <col=800000>Al Kharid</col>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        val stage = player.player.sleepingGiantsStage
        line("<red>Kovac</red> wants to restore a workshop built by his ancestors.")
        when {
            stage < SleepingGiantsState.REPAIRS -> line("I should follow Kovac into the cave.")
            stage < SleepingGiantsState.TUTORIAL -> {
                for (repair in SleepingGiantsData.repairs) {
                    val text = "Repair the ${repair.name}."
                    if (player.vars[repair.state] >= 2) strike(text) else line(text)
                }
                line("I should speak to Kovac when all three tools are repaired.")
            }
            player.player.sleepingGiantsTutorial >= 55 -> line("I have given Kovac the sword. I should speak to him again.")
            else -> {
                strike("I helped Kovac repair the workshop.")
                line("Kovac needs a <red>Flat, Broad</red> sword for his friend.")
                line(SleepingGiantsService.instruction(player.player))
            }
        }
    }

    override fun completedLog(player: ProtectedAccess) = completionJournal(player) {
        line("I helped Kovac restore his ancestors' foundry and forged a sword for his friend Obor.")
        line("Kovac hopes that his work will help the giants remember their history.")
    }
}
