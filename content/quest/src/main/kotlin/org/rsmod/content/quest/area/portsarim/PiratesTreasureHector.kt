package org.rsmod.content.quest.area.portsarim

import jakarta.inject.Inject
import org.rsmod.api.mechanics.dig.DigSites
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.Casket
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.ChestKey
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.ChestOpenSound
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.Complete
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.KeyObtained
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.LockedSound
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.MessageRead
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.PirateMessage
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.SearchRevealTicks
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.TakeFromContainerSeq
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.quest
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.stage
import org.rsmod.content.quest.manager.QuestVar
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PiratesTreasureHector
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(Chest) { openChest(it.loc) }
        onOpLocU(Chest, ChestKey) { unlockChest(it.loc) }

        onOpHeld1(PirateMessage) { readMessage() }

        DigSites.register(FaladorParkDigSite, TreasureTile) { digFaladorPark() }
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()

        if (inv.count(ChestKey) > 0) {
            unlockChest(chest)
            return
        }

        mes("The chest is locked.")
        soundSynth(LockedSound)
    }

    private fun ProtectedAccess.flickChestOpen(chest: BoundLocInfo) {
        anim(TakeFromContainerSeq)
        locRepo.change(chest, ChestOpen, ChestOpenTicks)
    }

    private suspend fun ProtectedAccess.unlockChest(chest: BoundLocInfo) {
        arriveDelay()

        if (stage(player) >= MessageRead && inv.count(PirateMessage) > 0) {
            mes("You unlock the chest. There is nothing else in it.")
            soundSynth(ChestOpenSound)
            flickChestOpen(chest)
            return
        }

        mes("You unlock the chest.")
        mes("All that's in the chest is a message...")
        soundSynth(ChestOpenSound)
        flickChestOpen(chest)

        delay(SearchRevealTicks)

        if (invAdd(inv, PirateMessage, 1).failure) {
            return
        }
        mes("You take the message from the chest.")
    }

    private suspend fun ProtectedAccess.readMessage() {
        mesbox(
            "Seek the light where the statue points,<br>" +
                "and the cross that marks the sod.<br>" +
                "Dig where the four paths meet,<br>" +
                "in the park of the White Knights' god."
        )

        if (stage(player) == KeyObtained) {
            quest().setQuestStage(this, MessageRead)
        }
    }

    private suspend fun ProtectedAccess.digFaladorPark(): Boolean {
        if (stage(player) != MessageRead) {
            return false
        }

        if (gardenerIsUp()) {
            mes("I can't dig up anything with him attacking me!")
            delay(SearchRevealTicks)
            resetAnim()
            return true
        }

        if (!hasDugOnce()) {
            firstDig()
            return true
        }

        mes("You dig a hole in the ground...")
        delay(SearchRevealTicks)
        resetAnim()

        if (invAdd(inv, Casket, 1).failure) {
            mes("You don't have enough inventory space to dig up the treasure.")
            return true
        }

        mes("and find a little chest of treasure.")
        quest().setQuestStage(this, Complete)
        return true
    }

    private suspend fun ProtectedAccess.firstDig() {
        val gardener = Npc(GardenerType, GardenerSpawn)
        npcRepo.add(gardener, GardenerLifespan)
        gardener.say("First moles, now this! Take this, vandal!")
        gardener.opPlayer2(player, aiPlayerInteractions)

        markDugOnce()

        delay(1)
        resetAnim()
    }

    private fun gardenerIsUp(): Boolean =
        npcRepo.findAll(ZoneKey.from(TreasureTile), zoneRadius = 1).any {
            it.isVisType(GardenerType)
        }

    private fun ProtectedAccess.hasDugOnce(): Boolean = DUG_ONCE.get(player)

    private fun ProtectedAccess.markDugOnce() {
        DUG_ONCE.set(player, true)
    }

    private companion object {
        private const val Chest = "loc.piratechest"
        private const val ChestOpen = "loc.piratechestopen"

        private const val ChestOpenTicks = SearchRevealTicks + 1

        private const val FaladorParkDigSite = "quest_piratestreasure.falador_park"

        private val TreasureTile = CoordGrid(2999, 3383, 0)

        private const val GardenerType = "npc.pirate_irate_gardener"

        private val GardenerSpawn = CoordGrid(2998, 3383, 0)

        private const val GardenerLifespan = 200

        private val DUG_ONCE = QuestVar.boolean("DUG_ONCE", "varp.pirates_treasure_dug_once", legacyKey = "pirates_treasure_dug_once", migratedVarp = "varp.pirates_treasure_dug_once_migrated")
    }
}
