package org.rsmod.content.quest.area.portsarim.pandemonium

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.quest.area.icemountain.musicRow
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.skills.sailing.SailingIntroVoyage
import org.rsmod.content.skills.sailing.SailingQuestMilestone
import org.rsmod.content.skills.sailing.SailingQuestProgressEvent
import org.rsmod.content.skills.sailing.SailingQuestSupport
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class Pandemonium @Inject internal constructor(
    private val voyage: SailingIntroVoyage,
    private val cutscenes: PandemoniumCutscenes,
    private val sailing: SailingQuestSupport,
    private val world: WorldRepository,
    private val npcs: NpcRepository,
    private val music: MusicPlayer,
    private val clock: MapClock,
) : QuestScript(
    "quest_pandemonium",
    "varp.sailing_intro_primary",
    rewards {
        xp("stat.sailing", 300.0)
        item("obj.sawmill_coupon", 25)
        extra("Your own raft")
        extra("Access to Sailing")
    },
    ItemRewardDisplay("obj.sawmill_coupon"),
    questVarbit = "varbit.sailing_intro",
    completionJingle = 152,
    completionJingleLength = 6325,
) {
    override fun ScriptContext.init() {
        for (npc in listOf("anne", "will")) {
            onOpNpc1("npc.sailing_intro_${npc}_sarim") { recruit() }
            onOpNpc1("npc.sailing_intro_${npc}_boat") { aboard() }
            onOpNpc1("npc.sailing_intro_${npc}_vis") {
                if (voyage.entity(player) != null) aboard() else recruit()
            }
        }
        for (npc in listOf("npc.steve_beanie", "npc.steve_beanie_1op", "npc.steve_beanie_2ops")) {
            onOpNpc1(npc) { steve() }
        }
        for (npc in listOf("npc.sailing_intro_ribs", "npc.sailing_intro_ribs_vis")) {
            onOpNpc1(npc) { ribs() }
        }
        for (npc in listOf("npc.junior_jim", "npc.junior_jim_1op", "npc.junior_jim_4ops", "npc.junior_jim_shipyard")) {
            onOpNpc1(npc) { jim() }
            onApNpc1(npc) { if (isWithinApRange(it.npc, 2)) jim() }
        }
        onOpLoc1("loc.sailing_intro_helm") { helm(it.loc) }
        onOpLoc1("loc.sailing_intro_helm_not_in_use") { helm(it.loc) }
        onOpLoc1("loc.sailing_intro_navigating") { helm(it.loc) }
        onOpLoc1("loc.sailing_intro_salvaging_hook") { salvage(it.loc) }
        onOpLoc1("loc.sailing_intro_salvaging_hook_ops") { salvage(it.loc) }
        for (sail in listOf("loc.sailing_boat_sails_intro")) {
            onOpLoc1(sail) { if (voyage.entity(player)?.let { boat -> it.loc.coords in boat } == true) voyage.trim(player) }
            onOpLoc2(sail) { if (stage(player) == SAILING && voyage.entity(player)?.let { boat -> it.loc.coords in boat } == true) voyage.setSails(player, true) }
            onOpLoc5(sail) { if (voyage.entity(player)?.let { boat -> it.loc.coords in boat } == true) voyage.setSails(player, false) }
        }
        onPlayerQueue(WRECK_QUEUE) { wreck() }
        onPlayerQueue(COMPLETE_QUEUE) {
            if (stage(player) == FINISHING) quest.completeQuest(this)
        }
        onPlayerLogin {
            if (stage(player) == FINISHING) player.queue(COMPLETE_QUEUE, 1)
        }
        onPlayerLogout { voyage.clearActors(player) }
        onEvent<GameLifecycle.LateCycle> {
            voyage.sweep()
            for (player in voyage.players()) {
                if (stage(player) == SAILING && voyage.reachedWreck(player) && WRECK_QUEUE !in player.queueList) {
                    voyage.halt(player)
                    player.queue(WRECK_QUEUE, 1)
                }
            }
        }
        onPlayerCoordsChanged {
            val current = stage(player)
            val coords = player.coords
            val next = when {
                current == ASSIGNED && voyage.aboardOwnedBoat(player) -> VOYAGING
                current in ASSIGNED..VOYAGING && coords.level == 0 &&
                    coords.x in 3020..3065 && coords.z in 3180..3235 -> SARIM
                current == COLLECTED && coords.level == 0 &&
                    coords.x in 3030..3085 && coords.z in 2950..3005 -> RETURNED
                else -> null
            }
            if (next != null) quest.jumpToStage(player, next)
        }
        onEvent<SailingQuestProgressEvent> {
            val current = stage(access.player)
            val next = when (milestone) {
                SailingQuestMilestone.CargoHoldBuilt -> if (current == RAFT) HOLD else null
                SailingQuestMilestone.CargoCollected -> if (current in SARIM..RETURNED) COLLECTED else null
                SailingQuestMilestone.CargoDelivered -> if (current in COLLECTED..RETURNED) DELIVERED else null
            }
            if (next != null) quest.setQuestStage(access, next)
        }
    }

    internal fun stage(player: Player): Int = quest.getQuestStage(player)

    private suspend fun ProtectedAccess.scene(scene: PandemoniumScene) {
        startDialogue { playPandemoniumScene(scene) }
    }

    private suspend fun ProtectedAccess.recruit() {
        if (stage(player) >= BETRAYED) {
            mes("Will and Anne have already left Port Sarim.")
            return
        }
        if (stage(player) == 0) {
            scene(PandemoniumScene.INTRODUCTION)
            if (!choice2("Start Pandemonium?", true, "Not right now.", false)) return
            quest.setQuestStage(this, ACCEPTED)
        }
        if (stage(player) == ACCEPTED) {
            scene(PandemoniumScene.INTERVIEW)
            quest.setQuestStage(this, INTERVIEWED)
        }
        if (!choice2("I'm ready to board.", true, "I'll come back later.", false)) return
        scene(PandemoniumScene.BOARDING)
        if (!voyage.board(this, stage(player) >= AT_WRECK)) {
            mes("The boat is unavailable just now. Please try again.")
            return
        }
        musicRow("Crest of a Wave")?.let { music.unlockAndPlay(player, it) }
        if (stage(player) < ABOARD) quest.setQuestStage(this, ABOARD)
        aboard()
    }

    private suspend fun ProtectedAccess.aboard() {
        if (voyage.entity(player) == null) return
        when (stage(player)) {
            ABOARD -> {
                scene(PandemoniumScene.ABOARD)
                quest.setQuestStage(this, BRIEFED)
            }
            BRIEFED, SAILING -> mes("Take the helm and sail south to the wreck east of the Karamja Shipyard.")
            AT_WRECK -> wreck()
            SALVAGE -> mes("Lower the salvaging hook to search the wreck.")
        }
    }

    private fun ProtectedAccess.helm(loc: BoundLocInfo) {
        val boat = voyage.entity(player) ?: return
        if (loc.coords !in boat) return
        if (stage(player) < BRIEFED) {
            mes("Speak to Will or Anne before taking the helm.")
            return
        }
        if (stage(player) >= AT_WRECK) {
            mes("You've reached the wreck. Speak to Will and Anne, then lower the salvaging hook.")
            return
        }
        if (stage(player) == BRIEFED) quest.setQuestStage(this, SAILING)
        faceLoc(loc)
        voyage.takeHelm(this)
    }

    private suspend fun ProtectedAccess.wreck() {
        if (voyage.entity(player) == null || stage(player) !in SAILING..AT_WRECK) return
        if (stage(player) == SAILING && !voyage.reachedWreck(player)) return
        voyage.halt(player)
        quest.setQuestStage(this, AT_WRECK)
        VarPlayerIntMapSetter.set(player, "varbit.sailing_intro_reached_wreck", 1)
        scene(PandemoniumScene.WRECK)
        quest.setQuestStage(this, SALVAGE)
    }

    private suspend fun ProtectedAccess.salvage(loc: BoundLocInfo) {
        val boat = voyage.entity(player) ?: return
        if (loc.coords !in boat) return
        if (stage(player) != SALVAGE) {
            mes("Talk to Will and Anne at the wreck before lowering the hook.")
            return
        }
        if (listOf(CUP, MEDALLION).any { bank.count(it) > 0 }) {
            mes("Bring the salvaged cup and medallion from your bank before continuing.")
            return
        }
        val missing = listOf(CUP, MEDALLION).filter { inv.count(it) == 0 }
        if (inv.freeSpace() < missing.size) {
            mes("You need two free inventory spaces to collect the salvage.")
            return
        }
        voyage.halt(player)
        faceLoc(loc)
        try {
            anim("seq.sailing_human_salvage_hook_kandarin_2x5_drop01")
            locAnim(world, loc, "seq.sailing_boats_salvage_hook_kandarin_2x5_drop01")
            delay(3)
            anim("seq.sailing_human_salvage_hook_kandarin_2x5_reset01")
            locAnim(world, loc, "seq.sailing_boats_salvage_hook_kandarin_2x5_reset01")
            delay(2)
            for (item in missing) invAdd(inv, item, 1)
            mesbox("You use the salvaging hook to gather an old cup and a mysterious medallion.")
            if (cutscenes.betray(this) { access ->
                access.invDel(access.inv, MEDALLION, access.inv.count(MEDALLION))
                quest.setQuestStage(access, BETRAYED)
            }) {
                musicRow("Yo Ho Ho!")?.let { music.unlockAndPlay(player, it) }
            }
        } finally {
            resetAnim()
            if (voyage.entity(player) === boat) {
                locAnim(world, loc, "seq.sailing_boats_salvage_hook_kandarin_2x5_reset01")
            }
        }
    }

    private suspend fun ProtectedAccess.steve() {
        when (stage(player)) {
            BETRAYED, 18 -> {
                scene(PandemoniumScene.STEVE)
                quest.setQuestStage(this, RIBS)
            }
            BOUNTY -> {
                scene(PandemoniumScene.STEVE_BOUNTY)
                quest.setQuestStage(this, JIM)
            }
            READY_GROG -> meetGrog()
            FINISHING -> queue(COMPLETE_QUEUE, 1)
            in 50..Int.MAX_VALUE -> mesbox("Old Grog is tracking Will and Anne. In the meantime, there are seas to explore!")
            else -> mesbox("Ribs knows about the stolen map. Junior Jim can help you get ready to sail.")
        }
    }

    private suspend fun ProtectedAccess.ribs() {
        if (stage(player) == RIBS) {
            scene(PandemoniumScene.RIBS)
            quest.setQuestStage(this, BOUNTY)
        } else {
            mesbox("Ribs has many tales of the sea. Steve can tell you about the missing map.")
        }
    }

    private suspend fun ProtectedAccess.jim() {
        when (stage(player)) {
            JIM, 26 -> {
                if (inv.count(CUP) == 0) {
                    if (bank.count(CUP) > 0) {
                        mesbox("The salvaged cup is in your bank. Bring it to Junior Jim.")
                        return
                    }
                    if (!choice2("I've lost the salvaged cup.", true, "I'll come back later.", false)) return
                    if (invAdd(inv, CUP, 1).failure) {
                        mes("Make room for the salvaged cup first.")
                        return
                    }
                    mesbox("Jim hands you an old cup recovered from the shore.")
                }
                scene(PandemoniumScene.JIM)
                if (inv.count(CUP) == 0 || !sailing.grantRaft(this)) return
                invDel(inv, CUP, 1)
                quest.setQuestStage(this, RAFT)
                mesbox("You give the cup to Junior Jim.")
                scene(PandemoniumScene.JIM_SHIPYARD)
                if (sailing.enterShipyard(this)) scene(PandemoniumScene.BUILD_INSTRUCTIONS)
            }
            RAFT -> {
                if (sailing.hasCargoHold(player)) {
                    quest.setQuestStage(this, HOLD)
                    jim()
                } else if (sailing.enterShipyard(this)) {
                    scene(PandemoniumScene.BUILD_INSTRUCTIONS)
                }
            }
            HOLD, LOG -> {
                scene(PandemoniumScene.CARGO_LOG)
                if (inv.count(LOG_BOOK) == 0 && bank.count(LOG_BOOK) == 0 && invAdd(inv, LOG_BOOK, 1).failure) {
                    mes("Make room for your captain's log first.")
                    return
                }
                quest.setQuestStage(this, LOG)
                if (!sailing.assignCargoTask(player)) {
                    mes("Finish one of your active port tasks to make room for Jim's delivery.")
                    return
                }
                quest.setQuestStage(this, ASSIGNED)
                scene(PandemoniumScene.CARGO_ASSIGNMENT)
            }
            in ASSIGNED..RETURNED -> {
                if (inv.count(LOG_BOOK) == 0 && bank.count(LOG_BOOK) == 0) invAdd(inv, LOG_BOOK, 1)
                sailing.assignCargoTask(player)
                mesbox("Collect my crate of ship parts from the Port Sarim loading bay. Bring it back to the Pandemonium port ledger.")
            }
            DELIVERED -> {
                if (player.vars["varbit.sailing_intro_received_repairkits"] == 0) {
                    if (!sailing.giveChartingSupplies(this)) return
                    VarPlayerIntMapSetter.set(player, "varbit.sailing_intro_received_repairkits", 1)
                }
                quest.setQuestStage(this, SUPPLIES)
                scene(PandemoniumScene.POST_DELIVERY)
                quest.setQuestStage(this, READY_GROG)
            }
            READY_GROG, FINISHING -> mesbox("Steve is waiting for you in the tavern. Old Grog should be back now.")
            else -> mesbox("I can help you look after your boat. Use my Buy or Upgrade options to get started.")
        }
    }

    private suspend fun ProtectedAccess.meetGrog() {
        val grog = Npc("npc.sailing_intro_grog_vis", CoordGrid(3049, 2968, 0))
        npcs.add(grog, Int.MAX_VALUE)
        grog.respawns = false
        grog.assignSpawnOwner(player, clock.cycle)
        try {
            lockedScene(
                CoordGrid(3049, 2967, 0), grog.coords,
                SceneCamera(CoordGrid(3045, 2963, 0), 650, CoordGrid(3050, 2967, 0), 100),
                exitCut = true,
            ) {
                grog.facePlayer(player)
                scene(PandemoniumScene.GROG)
                grog.walk(CoordGrid(3054, 2968, 0))
                delay(4)
            }
            quest.setQuestStage(this, FINISHING)
            try {
                scene(PandemoniumScene.COMPLETION)
            } finally {
                queue(COMPLETE_QUEUE, 1)
            }
        } finally {
            if (grog.isSlotAssigned) npcs.del(grog, Int.MAX_VALUE)
        }
    }

    override fun subTitle(): String = "speaking to <col=800000>Will and Anne</col> in Port Sarim."

    override fun questLog(player: ProtectedAccess): String = questJournal(player) {
        line(when (stage(player.player)) {
            0 -> "Will and Anne in Port Sarim are looking for someone to join their crew."
            in ACCEPTED..INTERVIEWED -> "I should finish my interview and tell Will and Anne I am ready to board."
            in ABOARD..BRIEFED -> "Will and Anne want to explain my new job aboard their boat."
            SAILING -> "I must take the helm and sail south, to the wreck east of the Karamja Shipyard."
            in AT_WRECK..SALVAGE -> "I should speak to Will and Anne and lower the salvaging hook beside the wreck."
            BETRAYED, 18 -> "Will and Anne threw me overboard! I should speak to Steve at the Pandemonium."
            RIBS -> "Ribs knows about the map Will and Anne stole. I should ask him about it."
            BOUNTY -> "Ribs says the medallion is cursed. Steve may know where Will and Anne have gone."
            JIM, 26 -> "Junior Jim at the docks can help me get a boat. I should bring him my salvaged cup."
            RAFT -> "I need to build a cargo hold for my boat in the shipyard. Jim will provide the materials."
            HOLD, LOG -> "I should speak to Junior Jim again for my captain's log and delivery task."
            in ASSIGNED..SARIM -> "I need to sail to Port Sarim and collect Jim's crate at the loading bay."
            COLLECTED, RETURNED -> "I should bring the ship parts back to the Pandemonium port ledger, then talk to Jim."
            DELIVERED -> "The cargo has been delivered. Junior Jim wants to speak to me."
            else -> "I should return to Steve at the tavern and meet Old Grog."
        })
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line("I survived Will and Anne's betrayal, built a cargo hold and completed my first delivery.")
        line("Old Grog is tracking the thieves. I have a boat and new seas to explore.")
    }

    companion object {
        const val ACCEPTED = 2
        const val INTERVIEWED = 4
        const val ABOARD = 6
        const val BRIEFED = 8
        const val SAILING = 10
        const val AT_WRECK = 12
        const val SALVAGE = 14
        const val BETRAYED = 16
        const val RIBS = 20
        const val BOUNTY = 22
        const val JIM = 24
        const val RAFT = 28
        const val HOLD = 30
        const val LOG = 32
        const val ASSIGNED = 34
        const val VOYAGING = 36
        const val SARIM = 38
        const val COLLECTED = 40
        const val RETURNED = 42
        const val DELIVERED = 44
        const val SUPPLIES = 44
        const val READY_GROG = 46
        const val FINISHING = 48
        private const val CUP = "obj.sailing_intro_cup"
        private const val MEDALLION = "obj.sailing_intro_medallion"
        private const val LOG_BOOK = "obj.sailing_log"
        private const val WRECK_QUEUE = "queue.pandemonium_wreck"
        private const val COMPLETE_QUEUE = "queue.pandemonium_complete"
    }
}
