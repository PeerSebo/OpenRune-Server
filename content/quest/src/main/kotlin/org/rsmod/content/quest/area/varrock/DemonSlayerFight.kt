package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcServerType
import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.table.MusicRow
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.SceneSnapRate
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class DemonSlayerFight(
    private val quest: Quest,
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val protectedAccess: ProtectedAccessLauncher,
    private val musicPlayer: MusicPlayer,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val alignment: TextAlignment,
    private val collision: CollisionFlagMap,
) {
    private var Player.delrithSummoned by intVarBit(DemonSlayerRefs.VarbitDelrithSummoned)

    private val encounters = mutableMapOf<Long, Encounter>()

    fun ScriptContext.register() {
        onPlayerCoordsChanged { player.onCoordsChanged() }

        onNpcQueue(delrithType, "queue.death") { onDelrithDefeated() }

        onOpNpc1(DemonSlayerRefs.DelrithWeakened) { banishOp(it.npc) }
        onOpNpc1(DemonSlayerRefs.Delrith) { banishOp(it.npc) }

        onPlayerLogin { discardEncounter(player) }
    }

    private fun Player.onCoordsChanged() {
        val encounter = uuid?.let(encounters::get)
        if (encounter != null) {
            val region = encounter.region
            if (region == null || !region.contains(coords)) {
                Camera.camReset(this)
                discardEncounter(this)
                loginExitCoord = null
            }
            return
        }
        if (coords.level != 0 || coords.x !in CircleTriggerX || coords.z !in CircleTriggerZ) {
            return
        }
        if (quest.getQuestStage(this) != DemonSlayerStage.KeysNamed) {
            return
        }
        if (!carriesSilverlight()) {
            return
        }
        protectedAccess.launch(this) { summoning() }
    }

    private fun Player.carriesSilverlight(): Boolean {
        return inv.count(DemonSlayerRefs.Silverlight) > 0 ||
            worn.count(DemonSlayerRefs.Silverlight) > 0
    }

    private suspend fun ProtectedAccess.summoning() {
        delay(1)

        // Clear pending combat before entering the scene so a queued wizard hit cannot interrupt it.
        combatClearQueue()

        val entryTile = player.coords
        val region = regionRepo.add(DemonSlayerCircleTemplate)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)

        val encounter = Encounter(player, region, scene, entryTile)
        player.uuid?.let { encounters[it] = encounter }

        player.loginExitCoord = DemonSlayerCoords.FightExit

        try {
            lockCutsceneHud()
            telejump(scene[entryTile])
            rebuildAppearance()

            val delrithTrack = MusicRow.getRow(DemonSlayerRefs.MusicDelrith)
            musicPlayer.unlock(player, delrithTrack)
            musicPlayer.play(player, delrithTrack)

            spawnCast(encounter)
            delay(1)

            frame(scene, SummoningScene.Opening, SceneSnapRate)
            camMoveToV3(
                scene[SummoningScene.OpeningDrift],
                height = SummoningScene.Opening.eyeHeight,
                rate = 1,
                rate2 = 1,
            )

            startDialogue {
                chatNpcSpecific(
                    DenathName,
                    DemonSlayerRefs.Denath,
                    happy,
                    "Arise, O mighty Delrith! Bring destruction to this soft, weak city!",
                )
                chatNpcSpecific(DarkWizardsName, DemonSlayerRefs.Wizard2, happy, "Arise, Delrith!")
            }

            summonDelrith(encounter)

            startDialogue {
                chatNpcSpecific(
                    DenathName,
                    DemonSlayerRefs.Denath,
                    laugh,
                    "Ha ha ha! At last you are free, my demonic brother! Rest now, and then have " +
                        "your revenge on this pitiful city!",
                )
                chatNpcSpecific(DarkWizardName, DemonSlayerRefs.Wizard1, shocked, "Who's that?")
                chatNpcSpecific(
                    DenathName,
                    DemonSlayerRefs.Denath,
                    worried,
                    "Noo! Not Silverlight! Delrith is not ready yet!",
                )
            }

            camMoveToV3(scene[SummoningScene.DenathFlees], height = 1000, rate = 1, rate2 = 1)
            encounter.denath?.walk(scene[DemonSlayerCoords.DenathExit])
            startDialogue {
                chatNpcSpecific(
                    DenathName,
                    DemonSlayerRefs.Denath,
                    shifty,
                    "I've got to get out of here...",
                )
            }

            // The varbit preserves rubble across zone refreshes; temporary loc changes do not.
            player.delrithSummoned = 1
            encounter.despawnDenath()
            unlockCutsceneHud()

            encounter.wizards.forEach { it.setHunt(it.type.huntRange) }

            encounter.wizards.getOrNull(0)?.apPlayer2(player, aiPlayerInteractions)
            encounter.wizards.getOrNull(1)?.defaultMode()
            encounter.wizards.getOrNull(2)?.defaultMode()
        } catch (cancelled: Throwable) {
            abortEncounter(encounter)
            unlockCutsceneHud()
            if (region == null || region.contains(player.coords)) {
                telejump(entryTile)
                rebuildAppearance()
            }
            player.loginExitCoord = null
            throw cancelled
        }
    }

    private fun spawnCast(encounter: Encounter) {
        val scene = encounter.scene
        val altar = scene[DemonSlayerCoords.StoneTable]

        val wizards =
            listOf(
                    DemonSlayerRefs.Wizard1 to DemonSlayerCoords.Wizard1Tile,
                    DemonSlayerRefs.Wizard2 to DemonSlayerCoords.Wizard2Tile,
                    DemonSlayerRefs.Wizard3 to DemonSlayerCoords.Wizard3Tile,
                )
                .map { (type, tile) ->
                    val npc = spawn(type, scene[tile])
                    npc.faceSquare(altar, AltarWidth, AltarLength)
                    npc.anim(DemonSlayerRefs.SeqWizardChanting)
                    npc
                }

        val denath = spawn(DemonSlayerRefs.Denath, scene[DemonSlayerCoords.DenathTile])
        denath.faceSquare(altar, AltarWidth, AltarLength)
        denath.anim(DemonSlayerRefs.SeqWizardChanting)

        encounter.wizards.addAll(wizards)
        encounter.denath = denath
    }

    private suspend fun ProtectedAccess.summonDelrith(encounter: Encounter) {
        val scene = encounter.scene
        val altar = scene[DemonSlayerCoords.StoneTable]

        mesboxNp("The wizards cast an evil spell...")
        changeAltar(scene, DemonSlayerRefs.StoneTableExplosion)
        soundSynth(DemonSlayerRefs.SynthSummonNpc)
        soundSynth(DemonSlayerRefs.SynthTableExplosion)
        camLookAtV3(altar, height = 100, rate = SceneSnapRate, rate2 = SceneSnapRate)
        frameEye(scene, SummoningScene.ExplosionEye, 500)
        delay(1)

        animateAltar(scene, DemonSlayerRefs.SeqTableExplosion)
        delay(1)

        val altarTiles =
            listOf(
                DemonSlayerCoords.StoneTable,
                DemonSlayerCoords.StoneTable.translateZ(1),
                DemonSlayerCoords.StoneTable.translateX(1),
                DemonSlayerCoords.StoneTable.translateX(1).translateZ(1),
            )
        for (i in DemonSlayerCoords.CircleCorners.indices) {
            worldRepo.projAnim(
                ProjAnim(
                    spotanim = DemonSlayerRefs.SpotanimRockFlying.asRSCM(RSCMType.SPOTANIM),
                    startHeight = 0,
                    endHeight = 0,
                    startTime = 0,
                    endTime = RockFlightCycles,
                    angle = 0,
                    progress = 0,
                    sourceIndex = 0,
                    targetIndex = 0,
                    startCoord = scene[altarTiles[i]],
                    endCoord = scene[DemonSlayerCoords.CircleCorners[i]],
                )
            )
        }
        camShake(0, ExplosionShake, 0, 0)
        delay(1)

        for (corner in DemonSlayerCoords.CircleCorners) {
            spotanimMap(worldRepo, DemonSlayerRefs.SpotanimRockBlast, scene[corner])
        }
        camShakeReset(0)
        delay(ExplosionToSummonTicks)

        changeAltar(scene, DemonSlayerRefs.StoneTableSummoning)
        soundSynth(DemonSlayerRefs.SynthBreakTable, delay = BreakTableSynthDelay)
        soundSynth(DemonSlayerRefs.SynthDelrithAppear)
        camLookAtV3(altar, height = 400, rate = 1, rate2 = 1)

        val delrith = spawn(DemonSlayerRefs.Delrith, altar)
        delrith.anim(DemonSlayerRefs.SeqDelrithSummoned)
        encounter.delrith = delrith
        delay(SummonToRubbleTicks)

        changeAltar(scene, DemonSlayerRefs.StoneTableDestroyed)
        delay(RubbleToFrameTicks)

        frame(scene, SummoningScene.DemonReveal, SceneSnapRate)
        delay(1)

        encounter.wizards.forEach(Npc::resetAnim)
        encounter.denath?.resetAnim()
    }

    private fun StandardNpcAccess.onDelrithDefeated() {
        val delrith = npc
        val encounter = encounters.values.firstOrNull { it.delrith === delrith } ?: return
        val player = encounter.player
        if (!player.isSlotAssigned) {
            return
        }

        // A queued zero hit can re-arm death at zero HP; guard against opening banishment twice.
        if (delrith.visType.id != delrithType.id) {
            return
        }

        val launched = protectedAccess.launch(player) { banishment(encounter, delrith) }
        if (!launched) {
            queueDeath()
        }
    }

    private suspend fun ProtectedAccess.banishOp(weakened: Npc) {
        val encounter = player.uuid?.let(encounters::get) ?: return
        if (encounter.delrith !== weakened) {
            return
        }
        if (weakened.visType.id != delrithWeakenedType.id) {
            return
        }
        recite(encounter, weakened)
    }

    private suspend fun ProtectedAccess.banishment(encounter: Encounter, delrith: Npc) {
        delrith.transmog(delrithWeakenedType, Int.MAX_VALUE)
        delrith.copyStats(delrithWeakenedType)
        delrith.hitpoints = delrithWeakenedType.hitpoints
        delrith.noneMode()
        delrith.moveRestrict = MoveRestrict.NoMove
        delrith.facePlayer(player)
        delrith.showAllOps()
        soundSynth(DemonSlayerRefs.SynthPortalOpen)

        startDialogue { chatPlayer(angry, "Now what was that incantation again?") }
        recite(encounter, delrith)
    }

    private suspend fun ProtectedAccess.recite(encounter: Encounter, delrith: Npc) {
        val expected = DemonSlayerIncantation.order(player)
        val spoken = ArrayList<Int>(expected.size)

        for (index in expected.indices) {
            val words = DemonSlayerIncantation.Words
            val sub =
                choice5(
                    words[0],
                    1,
                    words[1],
                    2,
                    words[2],
                    3,
                    words[3],
                    4,
                    words[4],
                    5,
                    title = SelectWordTitle,
                )
            val word = DemonSlayerIncantation.wordAt(sub) ?: return
            spoken += sub - 1

            val punctuation = if (index == expected.lastIndex) "!" else "..."
            chatPlayerNp(neutralMesAnim, "$word$punctuation")
            delay(WordEchoTicks)
        }

        if (spoken != expected) {
            wrongIncantation(delrith)
            return
        }
        banish(encounter, delrith)
    }

    private suspend fun ProtectedAccess.wrongIncantation(delrith: Npc) {
        closeChat()
        mesbox("The vortex collapses. That was the wrong incantation.")
        delrith.resetTransmog()
        delrith.copyStats(delrithType)
        delrith.hitpoints = delrithType.hitpoints
        delrith.moveRestrict = delrithType.moveRestrict
        delrith.opPlayer2(player, aiPlayerInteractions)
    }

    private suspend fun ProtectedAccess.banish(encounter: Encounter, delrith: Npc) {
        closeChat()

        var exited = false
        fun leaveInstance() {
            if (exited) {
                return
            }
            exited = true
            val region = encounter.region
            discardEncounter(player)
            if (region != null && region.contains(player.coords)) {
                telejump(DemonSlayerCoords.FightExit)
                rebuildAppearance()
            }
            player.loginExitCoord = null
            unlockCutsceneHud()
        }

        try {
            mesboxNp("Delrith is sucked into the vortex...")
            delrith.anim(DemonSlayerRefs.SeqDelrithBanished)
            soundSynth(DemonSlayerRefs.SynthDelrithBanished, delay = BanishSynthDelay)

            delay(BanishHoldTicks)

            player.delrithSummoned = 0
            restoreAltar(encounter.scene)
            soundSynth(DemonSlayerRefs.SynthReplaceTable)
            if (delrith.isSlotAssigned) {
                npcRepo.del(delrith, Int.MAX_VALUE)
            }
            encounter.delrith = null

            mesbox("...back into the dark dimension from which he came.")

            fadeOverlay(
                startColour = 0,
                startTransparency = 255,
                endColour = 0,
                endTransparency = 0,
                clientDuration = ExitFadeDuration,
            )
            leaveInstance()
            delay(1)

            fadeOverlay(
                startColour = 0,
                startTransparency = 0,
                endColour = 0,
                endTransparency = 255,
                clientDuration = ExitFadeDuration,
            )
            mes("You scurry away from the enraged dark wizards having defeated the demon.")
            delay(1)

            anim(DemonSlayerRefs.SeqReceivingSilverlight)
            spotanim(DemonSlayerRefs.SpotanimSwordGlint)
            soundSynth(DemonSlayerRefs.SynthEquipSilverlight)

            midiSong("midi.stop_music")
            quest.completeQuest(this)
        } finally {
            leaveInstance()
        }
    }

    // Intermediate altar leaves are not selectable by the multiloc; send explicit swaps until the final varbit.
    private fun changeAltar(scene: SceneRegion, type: String) {
        val altar = findAltar(scene) ?: return
        locRepo.add(altar.coords, type, AltarLifespanTicks, altar.angle, altar.shape)
    }

    private fun ProtectedAccess.animateAltar(scene: SceneRegion, seq: String) {
        val altar = findAltar(scene) ?: return
        locAnim(worldRepo, altar, seq)
    }

    private fun restoreAltar(scene: SceneRegion) {
        val altar = findAltar(scene) ?: return
        locRepo.add(
            altar.coords,
            DemonSlayerRefs.StoneTable,
            Int.MAX_VALUE,
            altar.angle,
            altar.shape,
        )
    }

    private fun findAltar(scene: SceneRegion) =
        locRepo.findExact(scene[DemonSlayerCoords.StoneTable], LocShape.CentrepieceStraight)

    private class Encounter(
        val player: Player,
        val region: Region?,
        val scene: SceneRegion,
        val entryTile: CoordGrid,
    ) {
        val wizards: MutableList<Npc> = mutableListOf()
        var denath: Npc? = null
        var delrith: Npc? = null

        var released: Boolean = false
    }

    private fun spawn(type: String, coords: CoordGrid): Npc {
        val npc = Npc(type, coords)
        npcRepo.add(npc, EncounterLifespanTicks)
        npc.noneMode()
        npc.setHunt(0)
        return npc
    }

    private fun Encounter.despawnDenath() {
        denath?.takeIf { it.isSlotAssigned }?.let { npcRepo.del(it, Int.MAX_VALUE) }
        denath = null
    }

    private fun abortEncounter(encounter: Encounter) {
        release(encounter)
        encounter.player.uuid?.let(encounters::remove)
    }

    private fun discardEncounter(player: Player) {
        val encounter = player.uuid?.let(encounters::remove) ?: return
        release(encounter)
    }

    private fun release(encounter: Encounter) {
        if (encounter.released) {
            return
        }
        encounter.released = true
        try {
            encounter.despawnDenath()
            encounter.wizards.removeAll { !it.isSlotAssigned }
            encounter.wizards.forEach { npcRepo.del(it, Int.MAX_VALUE) }
            encounter.wizards.clear()
            encounter.delrith?.takeIf { it.isSlotAssigned }?.let { npcRepo.del(it, Int.MAX_VALUE) }
            encounter.delrith = null
        } finally {
            encounter.region?.let(regionRepo::unprotect)
        }
    }

    private fun ProtectedAccess.lockCutsceneHud() {
        hideEntityOps()
        hideTopLevel()
        hideHealthHud()
        clearHealthHud()
        minimapHideMap()
        camModeClose()
        maxDrawDistance(enabled = true)
        tempDisableAcceptAid()
        closeTopLevelTabsLenient()
        syncDrawDistance()
        camShakeResetAll()
    }

    private fun ProtectedAccess.unlockCutsceneHud() {
        camUnlock(unlock = false)
        camReset()
        camShakeResetAll()
        showEntityOps()
        restoreLastAcceptAid()
        maxDrawDistance(enabled = false)
        minimapReset()
        camModeReset()
        showTopLevel()
        showHealthHud()
        openTopLevelTabs()
        syncDrawDistance()
    }

    private fun ProtectedAccess.frame(scene: SceneRegion, camera: SceneCamera, rate: Int) {
        val mapped = scene.map(camera)
        camMoveToV3(mapped.eye, height = mapped.eyeHeight, rate = rate, rate2 = rate)
        camLookAtV3(mapped.lookAt, height = mapped.lookAtHeight, rate = rate, rate2 = rate)
    }

    private fun ProtectedAccess.frameEye(scene: SceneRegion, eye: CoordGrid, height: Int) {
        camMoveToV3(scene[eye], height = height, rate = SceneSnapRate, rate2 = SceneSnapRate)
    }

    private fun ProtectedAccess.chatPlayerNp(mesanim: MesAnimType, text: String) {
        val page = alignment.generateChatPageList(text).first()
        val lineHeight = alignment.chatLineHeight(page.lineCount)
        val chatanim = ServerCacheManager.getAnim(mesanim.variant(page.lineCount)) ?: return

        player.mes("${player.displayName}|${page.text}", ChatType.Dialogue)
        chatModalUnclamp = constants.modal_fixedwidthandheight
        ClientScripts.topLevelChatboxResetBackground(player)
        ifOpenSub("interface.chat_right", "component.chatbox:chatmodal", IfSubType.Modal)
        ifSetPlayerHead("component.chat_right:head")
        ifSetAnim("component.chat_right:head", chatanim)
        ifSetText("component.chat_right:name", player.displayName)
        ifSetText("component.chat_right:text", page.text)
        ClientScripts.ifSetTextAlign(
            player,
            "component.chat_right:text",
            alignH = 1,
            alignV = 1,
            lineHeight,
        )
        ifSetEvents("component.chat_right:continue", -1..-1)
        ifSetText("component.chat_right:continue", "")
    }

    private var ProtectedAccess.chatModalUnclamp by intVarBit("varbit.chatmodal_unclamp")

    private fun MesAnimType.variant(lines: Int): Int =
        when (lines) {
            1 -> len1
            2 -> len2
            3 -> len3
            else -> len4
        }

    private val neutralMesAnim by lazy {
        ServerCacheManager.getMesAnim("mesanim.neutral".asRSCM(RSCMType.MESANIM))
            ?: error("mesanim.neutral has no definition in the SERVER cache.")
    }

    private val delrithType: NpcServerType by lazy { npcType(DemonSlayerRefs.Delrith) }

    private val delrithWeakenedType: NpcServerType by lazy {
        npcType(DemonSlayerRefs.DelrithWeakened)
    }

    private fun npcType(type: String): NpcServerType =
        ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: error("Missing npc type: $type")

    private companion object {

        const val DenathName = "Denath"
        const val DarkWizardName = "Dark wizard"

        const val DarkWizardsName = "Dark wizards"

        const val SelectWordTitle = "Select a word"

        val CircleTriggerX = 3221..3233

        val CircleTriggerZ = 3364..3372

        const val AltarWidth = 2
        const val AltarLength = 2

        const val ExplosionShake = 15

        const val RockFlightCycles = 30

        const val ExplosionToSummonTicks = 3

        const val BreakTableSynthDelay = 10

        const val SummonToRubbleTicks = 5
        const val RubbleToFrameTicks = 2

        const val WordEchoTicks = 3

        const val BanishSynthDelay = 10

        const val BanishHoldTicks = 16

        const val ExitFadeDuration = 15

        const val AltarLifespanTicks = 3000

        const val EncounterLifespanTicks = 3000
    }
}

private fun Region.contains(coords: CoordGrid): Boolean =
    coords.x in southWest.x..northEast.x && coords.z in southWest.z..northEast.z

internal class DemonSlayerSilverlightGate @Inject constructor() : NpcAttackValidateHook {
    private val silverlight by lazy { DemonSlayerRefs.Silverlight.asRSCM(RSCMType.OBJ) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!npc.isType(DemonSlayerRefs.Delrith)) {
            return NpcAttackValidateResult.Pass
        }
        if (player.righthand?.id == silverlight) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("I'd better wield Silverlight first.")
    }
}

internal class DemonSlayerWizardKillHook @Inject constructor() : NpcDeathKillHook {
    private val wizardIds by lazy {
        setOf(
            DemonSlayerRefs.Wizard1.asRSCM(RSCMType.NPC),
            DemonSlayerRefs.Wizard2.asRSCM(RSCMType.NPC),
            DemonSlayerRefs.Wizard3.asRSCM(RSCMType.NPC),
        )
    }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id !in wizardIds) {
            return
        }
        val player = context.hero
        val killed = player.vars[DemonSlayerRefs.VarbitWizardsKilled]
        if (killed >= MaxTracked) {
            return
        }
        VarPlayerIntMapSetter.set(player, DemonSlayerRefs.VarbitWizardsKilled, killed + 1)
    }

    private companion object {
        const val MaxTracked = 3
    }
}

internal object SummoningScene {
    val Opening: SceneCamera =
        SceneCamera(
            eye = CoordGrid(3224, 3376, 0),
            eyeHeight = 475,
            lookAt = CoordGrid(3227, 3369, 0),
            lookAtHeight = 300,
        )

    val OpeningDrift: CoordGrid = CoordGrid(3231, 3376, 0)

    val ExplosionEye: CoordGrid = CoordGrid(3227, 3365, 0)

    val DemonReveal: SceneCamera =
        SceneCamera(
            eye = CoordGrid(3226, 3375, 0),
            eyeHeight = 500,
            lookAt = CoordGrid(3227, 3367, 0),
            lookAtHeight = 300,
        )

    val DenathFlees: CoordGrid = CoordGrid(3226, 3383, 0)

    val Cameras: List<SceneCamera> =
        listOf(
            Opening,
            DemonReveal,
            SceneCamera(OpeningDrift, 475, ExplosionEye, 500),
            SceneCamera(DenathFlees, 1000, DemonReveal.lookAt, 300),
        )
}
