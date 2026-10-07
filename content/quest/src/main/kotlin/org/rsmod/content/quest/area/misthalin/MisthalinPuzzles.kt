package org.rsmod.content.quest.area.misthalin

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.plugin.scripts.ScriptContext

internal class MisthalinPuzzles(private val quest: Quest) {

    // The selected row is temporary interface state; only the attempt counter persists.
    private val gemRowSlot = quest.temporaryInt("GEM_ROW_SLOT", "varp.misthalin_gem_row_slot")

    fun register(ctx: ScriptContext): Unit =
        with(ctx) {
            onOpLoc1("loc.mistmyst_piano") { openPiano() }

            onIfModalButton("component.mistmyst_piano:close_button") {
                ifCloseSub("interface.mistmyst_piano")
            }

            for (note in PianoNotes) {
                onIfModalButton(note.component) { playNote(note) }
            }
            for (gem in GemSwitches) {
                onIfModalButton(gem.component) { flipSwitch(gem) }
            }
        }

    private fun ProtectedAccess.openPiano() {
        ifOpenMainModal("interface.mistmyst_piano")
    }

    private suspend fun ProtectedAccess.playNote(note: PianoNote) {
        mes("You play ${note.article} ${note.letter}.")
        soundSynth(note.synth)

        if (quest.getQuestStage(player) != MisthalinStage.Note2Read) {
            return
        }

        val mask = mistmystPianoMask
        val index = Integer.bitCount(mask)
        if (index < PianoAnswer.size && note.component == PianoAnswer[index]) {
            mistmystPianoMask = mask or (1 shl index)
            vars[MisthalinPianoNoteVarbits[index]] = 1
        }

        val attempts = (mistmystPianoAttempts + 1) % PianoAnswer.size
        mistmystPianoAttempts = attempts
        if (attempts != 0) {
            return
        }

        if (mistmystPianoMask == PianoSolvedMask) {
            quest.setQuestStage(this, MisthalinStage.PianoSolved)
            mes("There is a clicking sound as the compartment on the piano unlocks.")
            clearPianoBits()
            ifCloseSub("interface.mistmyst_piano")
            return
        }

        mes("There is a clicking sound as the lock on the piano's compartment resets.")
        ifCloseSub("interface.mistmyst_piano")
        startDialogue {
            chatPlayer(
                neutral,
                "I don't think that's right.. Perhaps the clue the killer left behind could help?",
            )
        }
        clearPianoBits()
    }

    private fun ProtectedAccess.clearPianoBits() {
        mistmystPianoMask = 0
        mistmystPianoAttempts = 0
        for (varbit in MisthalinPianoNoteVarbits) {
            vars[varbit] = 0
        }
    }

    private suspend fun ProtectedAccess.flipSwitch(gem: GemSwitch) {
        mes("You flip the ${gem.label} switch.")
        soundSynth("synth.INTERFACE_SELECT")

        showFlippedGem(gem)

        if (quest.getQuestStage(player) != MisthalinStage.PanelRevealed) {
            return
        }

        val mask = mistmystGemMask
        val index = Integer.bitCount(mask)
        if (index < GemAnswer.size && gem.label == GemAnswer[index]) {
            mistmystGemMask = mask or (1 shl index)
            vars[MisthalinGemNoteVarbits[index]] = 1
        }

        val attempts = (mistmystGemAttempts + 1) % GemAnswer.size
        mistmystGemAttempts = attempts
        if (attempts != 0) {
            return
        }

        if (mistmystGemMask == GemSolvedMask) {
            quest.setQuestStage(this, MisthalinStage.GemPanelSolved)
            mes("There is a clicking sound as the compartment in the fireplace unlocks.")
            clearGemBits()
            ifCloseSub("interface.mistmyst_gem_puzzle")
            return
        }

        mes("There is a clicking sound as the lock on the panel resets.")
        ifCloseSub("interface.mistmyst_gem_puzzle")
        startDialogue {
            chatPlayer(
                neutral,
                "I don't think that's the right order.. Perhaps the clue the killer left behind " +
                    "could help?",
            )
        }
        clearGemBits()
    }

    private fun ProtectedAccess.showFlippedGem(gem: GemSwitch) {
        val slot = gemRowSlot.get(player)
        if (slot < GemRowSlots) {
            ifSetModel("component.mistmyst_gem_puzzle:selected_${slot + 1}", gem.model)
            gemRowSlot.set(player, slot + 1)
        }
    }

    fun openGemPanel(access: ProtectedAccess) {
        access.ifOpenMainModal("interface.mistmyst_gem_puzzle")
        gemRowSlot.set(access.player, 0)
    }

    private fun ProtectedAccess.clearGemBits() {
        mistmystGemMask = 0
        mistmystGemAttempts = 0
        for (varbit in MisthalinGemNoteVarbits) {
            vars[varbit] = 0
        }
    }

    private class PianoNote(val component: String, val letter: Char, val synth: String) {
        val article: String
            get() = if (letter in VowelSoundedNotes) "an" else "a"
    }

    private class GemSwitch(val component: String, val label: String, val model: Int)

    private companion object {
        private val PianoAnswer =
            listOf(
                "component.mistmyst_piano:label_d1",
                "component.mistmyst_piano:label_e1",
                "component.mistmyst_piano:label_a2",
                "component.mistmyst_piano:label_d1",
            )

        private const val PianoSolvedMask = 15

        private const val GemSolvedMask = 63

        private const val GemRowSlots = 6

        private val VowelSoundedNotes = setOf('A', 'E', 'F')

        private val GemAnswer = listOf("sapphire", "diamond", "zenyte", "emerald", "onyx", "ruby")

        private val PianoNotes =
            listOf(
                PianoNote("component.mistmyst_piano:label_c1", 'C', "synth.GRIM_PIANO_C4"),
                PianoNote("component.mistmyst_piano:label_d1", 'D', "synth.GRIM_PIANO_D4"),
                PianoNote("component.mistmyst_piano:label_e1", 'E', "synth.GRIM_PIANO_E4"),
                PianoNote("component.mistmyst_piano:label_f1", 'F', "synth.GRIM_PIANO_F4"),
                PianoNote("component.mistmyst_piano:label_g1", 'G', "synth.GRIM_PIANO_G4"),
                PianoNote("component.mistmyst_piano:label_a2", 'A', "synth.GRIM_PIANO_A4"),
                PianoNote("component.mistmyst_piano:label_b2", 'B', "synth.GRIM_PIANO_B4"),
                PianoNote("component.mistmyst_piano:label_c2", 'C', "synth.GRIM_PIANO_C5"),
                PianoNote("component.mistmyst_piano:label_d2", 'D', "synth.GRIM_PIANO_D5"),
                PianoNote("component.mistmyst_piano:label_e2", 'E', "synth.GRIM_PIANO_E5"),
                PianoNote("component.mistmyst_piano:label_f2", 'F', "synth.GRIM_PIANO_F5"),
                PianoNote("component.mistmyst_piano:label_g2", 'G', "synth.GRIM_PIANO_G5"),
                PianoNote("component.mistmyst_piano:label_a3", 'A', "synth.GRIM_PIANO_A5"),
                PianoNote("component.mistmyst_piano:label_b3", 'B', "synth.GRIM_PIANO_B5"),
            )

        private val GemSwitches =
            listOf(
                GemSwitch("component.mistmyst_gem_puzzle:diamond", "diamond", 32333),
                GemSwitch("component.mistmyst_gem_puzzle:onyx", "onyx", 32342),
                GemSwitch("component.mistmyst_gem_puzzle:zenyte", "zenyte", 32349),
                GemSwitch("component.mistmyst_gem_puzzle:ruby", "ruby", 2586),
                GemSwitch("component.mistmyst_gem_puzzle:sapphire", "sapphire", 32345),
                GemSwitch("component.mistmyst_gem_puzzle:emerald", "emerald", 32334),
            )
    }
}
