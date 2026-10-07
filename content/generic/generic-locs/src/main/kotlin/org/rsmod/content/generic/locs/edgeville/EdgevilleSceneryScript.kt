package org.rsmod.content.generic.locs.edgeville

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class EdgevilleSceneryScript @Inject constructor(
    private val locRepo: LocRepository,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.coffin") { coffin(it.loc, open = true) }
        onOpLoc1("loc.coffin_open") { coffin(it.loc, open = false) }
        onOpLoc1("loc.edgeville_wildy_lever") { enterWilderness(it.loc, EdgevilleLever) }
        onOpLoc1("loc.wildinlever") { enterWilderness(it.loc, ArdougneLever) }
        onOpLoc1("loc.wildoutlever_default") { returnFromWilderness(it.loc, edgeville = false) }
        onOpLoc1("loc.wildoutlever_diary") { returnFromWilderness(it.loc, edgeville = false) }
        onOpLoc2("loc.wildoutlever_diary") { returnFromWilderness(it.loc, edgeville = true) }
        onOpLoc1("loc.wildoutlever_diary_swapped") { returnFromWilderness(it.loc, edgeville = true) }
        onOpLoc2("loc.wildoutlever_diary_swapped") { returnFromWilderness(it.loc, edgeville = false) }
        for (variant in listOf("loc.wildoutlever_diary", "loc.wildoutlever_diary_swapped")) {
            onOpLoc3(variant) {
                if (it.loc.coords == KeepLever && player.hasLeverDiary()) {
                    vars[FirstOption] = 1 - vars[FirstOption].coerceIn(0, 1)
                    player.refreshLever()
                }
            }
        }
        onPlayerLogin { player.refreshLever() }
        onPlayerQueueWithArgs<CoordGrid>(LandingQueue) {
            val from = coords
            telejump(it.args, TeleportType.Lever)
            if (coords != from) anim("seq.human_castteleport_reverse") else resetAnim()
        }
    }

    private suspend fun ProtectedAccess.coffin(loc: BoundLocInfo, open: Boolean) {
        if (loc.coords !in Coffins) return
        arriveDelay()
        val replacement = if (open) "loc.coffin_open" else "loc.coffin"
        val entity = LocEntity(replacement.asRSCM(), loc.shape.id, loc.angle.id)
        if (locRepo.add(LocInfo(loc.layer, loc.coords, entity), duration = 500)) {
            soundSynth(if (open) "synth.COFFIN_OPEN" else "synth.COFFIN_CLOSE")
        }
    }

    private suspend fun ProtectedAccess.enterWilderness(loc: BoundLocInfo, source: CoordGrid) {
        if (loc.coords != source) return
        arriveDelay()
        if (!player.members) {
            mes("You need to be on a members' world to use this lever.")
            return
        }
        if (vars["varbit.wilderness_lever_blockwarning_standard"] == 0) {
            val accepted = choice2(
                "Yes, teleport me to the Wilderness.", true,
                "No, I don't want to go.", false,
                title = "Warning: this lever leads to deep Wilderness!",
            )
            if (!accepted) return
        }
        player.refreshLever()
        pullLever(loc, KeepLanding)
    }

    private suspend fun ProtectedAccess.returnFromWilderness(loc: BoundLocInfo, edgeville: Boolean) {
        if (loc.coords != KeepLever) return
        if (edgeville && !player.hasLeverDiary()) {
            mes("You need to complete the easy Wilderness Diary to teleport to Edgeville.")
            return
        }
        arriveDelay()
        pullLever(loc, if (edgeville) EdgevilleLanding else ArdougneLanding)
    }

    private suspend fun ProtectedAccess.pullLever(loc: BoundLocInfo, destination: CoordGrid) {
        if (!canTeleport()) return
        val wall = when (loc.angle) {
            LocAngle.West -> loc.coords.translateX(-1)
            LocAngle.North -> loc.coords.translateZ(1)
            LocAngle.East -> loc.coords.translateX(1)
            LocAngle.South -> loc.coords.translateZ(-1)
        }
        // Levers share the player's tile; resolve facing now even on a movement-arrival tick.
        val angle = player.calculateAngle(wall, 1, 1) ?: player.faceAngle
        player.resetPendingFaceSquare()
        player.faceAngle = angle
        player.pendingFaceAngle = EntityFaceAngle(angle)
        anim("seq.human_leverdown")
        locRepo.change(loc, "loc.magearena_lever_down", duration = 2)
        soundSynth("synth.LEVER")
        delay(1)
        if (!canTeleport()) return
        player.abortRoute()
        player.clearMapFlag()
        anim("seq.human_castteleport")
        spotanim("spotanim.teleport_casting", height = 92)
        soundSynth("synth.TELEPORT_ALL")
        actionDelay = mapClock + 4
        clearQueue(LandingQueue)
        // delay resumes before queue decrement; retain the landing if another coroutine replaces us.
        queue(LandingQueue, 5, destination)
    }

    private fun ProtectedAccess.canTeleport(): Boolean {
        val denial = teleportValidator.validate(player, TeleportType.Lever, areaChecker)
        if (denial == null) return true
        mes(denial)
        return false
    }

    private fun Player.hasLeverDiary(): Boolean = vars["varbit.wilderness_diary_easy_complete"] != 0

    private fun Player.refreshLever() {
        val variant = if (!hasLeverDiary()) 0 else 1 + vars[FirstOption].coerceIn(0, 1)
        VarPlayerIntMapSetter.set(this, "varbit.wildoutlever_vis", variant)
    }

    internal companion object {
        const val LandingQueue = "queue.wilderness_lever_land"
        private const val FirstOption = "varbit.wilderness_lever_first_op"
        val EdgevilleLever = CoordGrid(3090, 3475)
        val ArdougneLever = CoordGrid(2561, 3311)
        val KeepLever = CoordGrid(3153, 3923)
        val EdgevilleLanding = CoordGrid(3090, 3475)
        val ArdougneLanding = CoordGrid(2561, 3311)
        val KeepLanding = CoordGrid(3154, 3924)
        val Coffins = setOf(CoordGrid(3090, 3476), CoordGrid(3090, 3479), CoordGrid(3096, 3479))
    }
}
