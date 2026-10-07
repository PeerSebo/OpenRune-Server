package org.rsmod.content.minigames.giantsfoundry.quest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseSmithingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.MusicModernRow
import org.rsmod.content.minigames.giantsfoundry.FoundryGame
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMath
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryStation
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo

@Singleton
class SleepingGiantsService @Inject constructor(
    private val game: FoundryGame,
    private val workshop: SleepingGiantsWorkshop,
    private val cutscene: SleepingGiantsCutscene,
    private val music: MusicPlayer,
) {
    private val quest get() = checkNotNull(Quest.get(SleepingGiantsState.KEY))

    suspend fun start(access: ProtectedAccess, npc: Npc): Unit = with(access) {
        if (player.sleepingGiantsStage >= SleepingGiantsState.ACCEPTED) {
            talk(this, npc)
            return@with
        }
        if (player.baseSmithingLvl < 15) {
            mes("You need a Smithing level of 15 to help Kovac.")
            return@with
        }
        var accepted = false
        startDialogue(npc) {
            chatNpc(worried, "Wait... Do not attack! Kovac needs your help.")
            chatPlayer(shocked, "You can talk? What do you need help with?")
            chatNpc(neutral, "Found workshop. Giants built long ago. Help fix it?")
            accepted = choice2("Yes, I'll help you.", true, "Not right now.", false)
        }
        if (!accepted) return@with
        quest.setQuestStage(this, SleepingGiantsState.ACCEPTED)
        enterQuest(this)
    }

    suspend fun enterQuest(access: ProtectedAccess): Unit = with(access) {
        game.store(player)
        val npc = workshop.enter(this) ?: return@with
        music.unlockAndPlay(player, foundryMusic)
        if (player.sleepingGiantsStage < SleepingGiantsState.INSIDE) {
            quest.setQuestStage(this, SleepingGiantsState.INSIDE)
        }
        if (player.sleepingGiantsStage < SleepingGiantsState.REPAIRS) {
            cutscene.play(this, npc)
            quest.setQuestStage(this, SleepingGiantsState.REPAIRS)
        }
        mes("Repair the trip hammer, grindstone and polishing wheel, then speak to Kovac.")
    }

    suspend fun talk(access: ProtectedAccess, npc: Npc): Unit = with(access) {
        when (player.sleepingGiantsStage) {
            0 -> start(this, npc)
            in SleepingGiantsState.ACCEPTED until SleepingGiantsState.REPAIRS -> enterQuest(this)
            SleepingGiantsState.REPAIRS -> {
                if (SleepingGiantsData.repairs.any { player.vars[it.state] < 2 }) {
                    startDialogue(npc) {
                        chatNpc(neutral, "We fix tools first. Inspect each broken tool to see what it needs.")
                    }
                    return@with
                }
                quest.setQuestStage(this, SleepingGiantsState.REPAIRED)
                startDialogue(npc) {
                    chatNpc(happy, "Tools look right. Kovac begin to understand. Ready to make sword?")
                    chatPlayer(happy, "Yes! Let's get started.")
                }
                workshop.enterPublicWorkshop(player)
                quest.setQuestStage(this, SleepingGiantsState.TUTORIAL)
                beginTutorial(this)
            }
            SleepingGiantsState.REPAIRED -> {
                workshop.enterPublicWorkshop(player)
                quest.setQuestStage(this, SleepingGiantsState.TUTORIAL)
                beginTutorial(this)
            }
            SleepingGiantsState.TUTORIAL -> {
                if (player.sleepingGiantsTutorial >= 55) {
                    finish(this)
                } else if (FoundryGame.hasCommission(player) && !SleepingGiantsState.inTutorial(player)) {
                    mesbox("Finish or cancel your current commission before starting Kovac's first sword.")
                } else if (player.foundryCompletion >= 1000 ||
                    (player.foundryStartQuality > 0 && player.foundryQuality == 0)
                ) {
                    handIn(this)
                } else if (!FoundryGame.hasCommission(player)) {
                    beginTutorial(this)
                } else {
                    refreshTutorial(player)
                    startDialogue(npc) { chatNpc(neutral, instruction(player)) }
                }
            }
            else -> mes("Kovac is grateful for your help restoring the foundry.")
        }
    }

    private fun beginTutorial(access: ProtectedAccess) = with(access) {
        if (FoundryGame.hasCommission(player)) {
            mes("Finish or cancel your current commission before starting Kovac's first sword.")
            return@with
        }
        game.commission(player, random)
        player.sleepingGiantsTutorialCommission = true
        player.foundryWord1 = 3
        player.foundryWord2 = 4
        player.sleepingGiantsTutorial = maxOf(10, player.sleepingGiantsTutorial)
        if (player.sleepingGiantsTutorial < 15) {
            mes("Kovac needs a Flat, Broad sword. Take the metal from the crate by the entrance, " +
                "then fill the crucible with bronze and iron.")
        } else {
            mes("Kovac needs a Flat, Broad sword. You can use any remaining metal or bring " +
                "replacement bars and equipment to fill the crucible again.")
        }
    }

    internal fun takeSupplies(player: Player): Boolean {
        if (!SleepingGiantsState.inTutorial(player) || player.sleepingGiantsTutorial >= 15) return false
        val result = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            for ((item, count) in SleepingGiantsData.supplies) add(inventory, item.asRSCM(), count)
        }
        if (result.failure) return false
        player.sleepingGiantsTutorial = 15
        return true
    }

    suspend fun searchSupplies(access: ProtectedAccess): Boolean = with(access) {
        if (!SleepingGiantsState.inTutorial(player)) return@with false
        if (player.sleepingGiantsTutorial >= 15) {
            mes("You've taken the metal from this crate. Kovac can remind you what to do next.")
        } else if (takeSupplies(player)) {
            anim("seq.human_pickuptable")
            mes("You take a selection of bars and weapons from the crate.")
        } else {
            mes("You will need 20 free inventory slots to take the items from the crate.")
        }
        true
    }

    internal fun hasRepairTools(player: Player, repair: SleepingGiantsData.Repair): Boolean {
        if (repair.tool in player.inv) return true
        return repair.tool == "obj.hammer" &&
            listOf("obj.imcando_hammer", "obj.imcando_hammer_offhand").any {
                it in player.inv || it in player.worn
            }
    }

    internal fun consumeRepairMaterials(player: Player, repair: SleepingGiantsData.Repair): Boolean {
        if (player.sleepingGiantsStage != SleepingGiantsState.REPAIRS || player.vars[repair.state] >= 2 ||
            !hasRepairTools(player, repair)
        ) return false
        var remaining = repair.nails
        val nails = SleepingGiantsData.nails.map { item ->
            val count = minOf(remaining, player.inv.physicalCount(item))
            remaining -= count
            item to count
        }.filter { it.second > 0 }
        if (remaining > 0) return false
        val consumed = repair.materials + nails
        if (consumed.isNotEmpty()) {
            val result = player.invTransaction(player.inv) {
                val inventory = select(player.inv)
                for ((item, count) in consumed) delete(inventory, item.asRSCM(), count)
            }
            if (result.failure) return false
        }
        VarPlayerIntMapSetter.set(player, repair.state, 2)
        return true
    }

    internal suspend fun repair(access: ProtectedAccess, loc: BoundLocInfo, repair: SleepingGiantsData.Repair) = with(access) {
        if (!workshop.contains(player) || player.sleepingGiantsStage != SleepingGiantsState.REPAIRS) {
            mes("Speak to Kovac about restoring his ancestors' workshop first.")
            return@with
        }
        if (player.vars[repair.state] >= 2) {
            mes("The ${repair.name} has been repaired.")
            return@with
        }
        VarPlayerIntMapSetter.set(player, repair.state, 1)
        val materials = repair.materials.joinToString { (item, count) ->
            "$count ${checkNotNull(ServerCacheManager.getItem(item.asRSCM())).name.lowercase()}"
        }
        if (!hasRepairTools(player, repair) ||
            repair.materials.any { (item, count) -> player.inv.physicalCount(item) < count } ||
            SleepingGiantsData.nails.sumOf { player.inv.physicalCount(it) } < repair.nails
        ) {
            val nails = if (repair.nails > 0) ", ${repair.nails} nails" else ""
            val tool = checkNotNull(ServerCacheManager.getItem(repair.tool.asRSCM())).name.lowercase()
            mes("You need ${if (materials.isEmpty()) "a $tool" else "$materials$nails and a $tool"} to repair the ${repair.name}.")
            return@with
        }
        faceLoc(loc)
        for ((sequence, ticks) in repair.animations) {
            anim(sequence)
            delay(ticks)
        }
        if (!workshop.contains(player) || !consumeRepairMaterials(player, repair)) return@with
        resetAnim()
        mes("You repair the ${repair.name}.")
    }

    suspend fun handIn(access: ProtectedAccess): Boolean = with(access) {
        if (player.sleepingGiantsStage == SleepingGiantsState.TUTORIAL &&
            player.sleepingGiantsTutorial >= 55 && !FoundryGame.hasCommission(player)
        ) {
            finish(this)
            return@with true
        }
        if (!SleepingGiantsState.inTutorial(player)) return@with false
        if (player.foundryStored != 0) {
            mes("Take your preform from storage before bringing it to Kovac.")
            return@with true
        }
        if (!FoundryGame.isEquipped(player) || player.foundryStartQuality <= 0) {
            mes("Pick up your preform before bringing it to Kovac.")
            return@with true
        }
        if (player.foundryQuality == 0) {
            game.cancel(player)
            mes("The sword is ruined. Speak to Kovac to start again; you can bring replacement metal.")
            return@with true
        }
        if (player.foundryCompletion < 1000) {
            mes("Finish working on the sword before bringing it to Kovac.")
            return@with true
        }
        game.cancel(player)
        player.sleepingGiantsTutorial = 55
        finish(this)
        true
    }

    private suspend fun finish(access: ProtectedAccess): Unit = with(access) {
        if (quest.isQuestCompleted(player)) return@with
        mesbox("Kovac examines the sword. His friend Obor will show the other giants what their " +
            "ancestors' foundry can make. Kovac thanks you for helping him restore it.")
        player.sleepingGiantsTutorial = 65
        quest.completeQuest(this)
        Unit
    }

    companion object {
        private val foundryMusic by lazy {
            MusicModernRow.getRow("dbrow.music_modern_the_foundry").tracks.single()
        }

        internal fun refreshTutorial(player: Player) {
            if (!SleepingGiantsState.inTutorial(player)) return
            val state = when {
                FoundryGame.isEquipped(player) -> 50
                player.foundryStartQuality > 0 -> 45
                player.foundryMouldState == 1 -> 35
                FoundryGame.metalCounts(player).sum() == 28 -> 25
                FoundryGame.metalCounts(player).sum() > 0 -> 20
                else -> player.sleepingGiantsTutorial
            }
            player.sleepingGiantsTutorial = maxOf(player.sleepingGiantsTutorial, state)
        }

        internal fun instruction(player: Player): String = when {
            FoundryGame.hasCommission(player) && !SleepingGiantsState.inTutorial(player) -> "Finish or cancel your current commission, then ask Kovac to resume Sleeping Giants."
            player.sleepingGiantsTutorial >= 55 -> "Speak to Kovac to finish Sleeping Giants."
            !FoundryGame.hasCommission(player) -> "Speak to Kovac to receive a commission for the first sword."
            player.foundryStartQuality > 0 && player.foundryStored != 0 -> "Take your preform from storage to continue your sword."
            player.foundryStartQuality > 0 && player.foundryQuality == 0 -> "The sword is ruined. Bring it to Kovac and ask to start again."
            player.foundryCompletion >= FoundryMath.COMPLETE -> "The sword is finished. Bring it to Kovac."
            player.foundryStartQuality > 0 && player.foundryMouldState == 3 -> "The preform has cooled. Pick it up from the mould jig."
            player.foundryStartQuality > 0 && !FoundryGame.isEquipped(player) -> "Cool the preform in the mould with a bucket of water, or wear ice gloves, then pick it up."
            FoundryGame.isEquipped(player) -> workingInstruction(player)
            player.foundryMouldState == 1 && FoundryGame.metalCounts(player).sum() == 28 -> "Pour the crucible into the mould, cool the metal, then pick up the preform."
            FoundryGame.metalCounts(player).sum() == 28 -> "Set up the mould jig with a forte, blade and tip suited to a Flat, Broad sword."
            player.sleepingGiantsTutorial < 15 -> "Take the metal from the crate by the entrance. You will need 20 free inventory slots."
            else -> "Fill the crucible with the equivalent of 28 bronze and iron bars. Both bars and equipment can be melted down."
        }

        private fun workingInstruction(player: Player): String {
            val section = FoundryMath.section(player.foundryCompletion, player.foundryDifficulty)
            val station = FoundryState.station(player, section)
                ?: return "Speak to Kovac about the current commission before continuing."
            val tool = when (station) {
                FoundryStation.HAMMER -> "trip hammer"
                FoundryStation.GRINDSTONE -> "grindstone"
                FoundryStation.POLISHING_WHEEL -> "polishing wheel"
            }
            val temperature = FoundryMath.temperatureRange(station, player.foundryDifficulty)
            return when {
                player.foundryTemperature < temperature.first ->
                    "The sword is too cold for the $tool. Heat it in the lava pool until the temperature is in the highlighted range, then use the $tool."
                player.foundryTemperature > temperature.last ->
                    "The sword is too hot for the $tool. Cool it at the waterfall until the temperature is in the highlighted range, then use the $tool."
                else -> "The sword is at the right temperature. Use the $tool until the highlighted tool changes, keeping the temperature in its highlighted range."
            }
        }
    }
}
