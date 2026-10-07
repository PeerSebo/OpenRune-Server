package org.rsmod.content.skills.sailing.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.skills.sailing.CrewMate
import org.rsmod.content.skills.sailing.CrewRoster
import org.rsmod.content.skills.sailing.CrewService
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CrewScript @Inject constructor(private val crew: CrewService) : PluginScript() {
    override fun ScriptContext.startup() {
        for (mate in crew.roster) {
            for (npc in mate.talkToNpcs) {
                onOpNpc1(npc) { startDialogue(it.npc) { recruitDialogue(mate) } }
            }
        }

        onOpNpc1(CrewRoster.REGISTRAR_1OP) { startDialogue(it.npc) { registrarDialogue() } }
        onOpNpc1(CrewRoster.REGISTRAR_2OP) { startDialogue(it.npc) { registrarDialogue() } }
        onOpNpc3(CrewRoster.REGISTRAR_2OP) { openCrewInterface() }

        onIfModalButton("component.sailing_crew:recruits_list") { selectRecruit(it.comsub) }
        onIfModalButton("component.sailing_crew:crew_list") { selectSlot(it.comsub) }
        onIfModalButton("component.sailing_crew:assign") { assignSelected() }
        onIfModalButton("component.sailing_crew:clear") { clearSelected() }
        onIfClose(CrewRoster.INTERFACE_CREW) { crew.clearSelection(player) }
    }

    private suspend fun Dialogue.recruitDialogue(mate: CrewMate) {
        val firstMeeting = !crew.hasMet(player, mate)
        crew.markMet(player, mate)

        if (crew.isRecruited(player, mate)) {
            val slot = crew.slotOf(player, mate)
            if (slot == null) {
                chatNpc(neutral, CrewMessages.ALREADY_RECRUITED_ASHORE)
            } else {
                chatNpc(happy, CrewMessages.alreadyAssigned(slot))
            }
            return
        }

        if (firstMeeting) {
            chatNpc(happy, CrewMessages.greeting(mate))
        }

        if (!crew.canRecruit(player, mate)) {
            chatNpc(neutral, CrewMessages.needSailingLevel(mate.wikiSailingLevel))
            return
        }

        val join =
            choice2(
                "Yes, sign on.",
                true,
                "Not right now.",
                false,
                title = CrewMessages.recruitTitle(mate),
            )
        if (!join) {
            chatNpc(neutral, CrewMessages.DECLINED)
            return
        }

        crew.recruit(player, mate)
        chatNpc(happy, CrewMessages.RECRUITED)
        chatNpc(neutral, CrewMessages.SEE_THE_REGISTRAR)
    }

    private suspend fun Dialogue.registrarDialogue() {
        val slots = crew.unlockedCrewSlots(player)
        if (slots == 0) {
            chatNpc(neutral, CrewMessages.needCrewSlotLevel(crew.crewSlotRequirement(1)))
            return
        }
        val recruited = crew.recruited(player).size
        if (recruited == 0) {
            chatNpc(neutral, CrewMessages.NO_RECRUITS)
            return
        }
        val manage =
            choice2(
                "Yes, show me my crew.",
                true,
                "Not right now.",
                false,
                title = CrewMessages.registrarTitle(recruited, slots),
            )
        if (manage) {
            access.openCrewInterface()
        }
    }

    private fun ProtectedAccess.openCrewInterface() {
        if (crew.unlockedCrewSlots(player) == 0) {
            mes(CrewMessages.needCrewSlotLevel(crew.crewSlotRequirement(1)))
            return
        }
        crew.clearSelection(player)
        ifOpenMainModal(CrewRoster.INTERFACE_CREW)
    }

    private fun ProtectedAccess.selectRecruit(comsub: Int) {
        val mate = crew.unassignedRecruits(player).getOrNull(comsub) ?: return
        crew.selectCrew(player, mate)
    }

    private fun ProtectedAccess.selectSlot(comsub: Int) {
        val slot = comsub + 1
        if (slot !in 1..CrewRoster.MAX_CREW_SLOTS) {
            return
        }
        crew.selectSlot(player, slot)
    }

    private fun ProtectedAccess.assignSelected() {
        val slot = crew.selectedSlot(player) ?: return
        val mate = crew.selectedCrew(player) ?: return

        if (slot > crew.unlockedCrewSlots(player)) {
            mes(CrewMessages.needSlotUnlock(slot, crew.crewSlotRequirement(slot)))
            return
        }
        if (!crew.isRecruited(player, mate)) {
            mes(CrewMessages.NOT_RECRUITED)
            return
        }
        crew.assign(player, slot, mate)
        crew.selectCrew(player, null)
        spam(CrewMessages.assigned(mate, slot))
    }

    private fun ProtectedAccess.clearSelected() {
        val slot = crew.selectedSlot(player) ?: return
        val mate = crew.assigned(player, slot)
        crew.clear(player, slot)
        if (mate != null) {
            spam(CrewMessages.dismissed(mate))
        }
    }
}

internal object CrewMessages {
    const val ALREADY_RECRUITED_ASHORE: String =
        "I'm signed on already. Speak to the Crew Registrar when you want me aboard."

    const val RECRUITED: String = "Aye, I'll sail with you."

    const val SEE_THE_REGISTRAR: String =
        "Talk to the Crew Registrar when you want to put me on a boat."

    const val DECLINED: String = "Suit yourself. I'll be here."

    const val NO_RECRUITS: String =
        "You haven't signed anyone on yet. Find a sailor out there and ask them yourself."

    const val NOT_RECRUITED: String = "You haven't recruited that sailor."

    fun greeting(mate: CrewMate): String = "${mate.displayName}, at your service."

    fun alreadyAssigned(slot: Int): String = "I'm already on your crew, in slot $slot."

    fun needSailingLevel(level: Int): String =
        "You need a Sailing level of $level before I'll sail with you."

    fun needCrewSlotLevel(level: Int): String =
        "You need a Sailing level of $level to take on any crew."

    fun needSlotUnlock(slot: Int, level: Int): String =
        "You need a Sailing level of $level to use crew slot $slot."

    fun recruitTitle(mate: CrewMate): String = "Ask ${mate.displayName} to join your crew?"

    fun registrarTitle(recruited: Int, slots: Int): String =
        "You have $recruited signed on and $slots crew slots. Manage your crew?"

    fun assigned(mate: CrewMate, slot: Int): String =
        "${mate.displayName} joins your crew in slot $slot."

    fun dismissed(mate: CrewMate): String = "${mate.displayName} leaves your crew."
}
