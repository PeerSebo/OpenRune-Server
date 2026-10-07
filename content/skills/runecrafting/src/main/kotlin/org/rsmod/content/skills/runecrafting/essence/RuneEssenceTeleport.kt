package org.rsmod.content.skills.runecrafting.essence

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

const val PORTAL_VARBIT: String = "varbit.essencemine_portal"

enum class EssenceMineTeleporter(val portalVar: Int, private val returnCoord: CoordGrid) {
    Sedridor(portalVar = 0, returnCoord = CoordGrid(3104, 9570)),

    Aubury(portalVar = 1, returnCoord = CoordGrid(3253, 3401));

    fun returnDest(random: GameRandom): CoordGrid = EssencePortals.randomizeLocation(returnCoord, random)

    companion object {
        fun fromPortalVar(value: Int): EssenceMineTeleporter? =
            entries.find { it.portalVar == value }
    }
}

class EssencePortals @Inject constructor(private val worldRepo: WorldRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.blankrunestone_exit_portal") { teleportToMainLand(worldRepo, it.loc.coords) }
    }

    companion object {

        private val RUNE_ESSENCE_MINE = CoordGrid(2910, 4832)
        private const val LANDING_RADIUS = 20

        fun randomEssenceMineDest(access: ProtectedAccess): CoordGrid =
            access.mapFindSquareNone(RUNE_ESSENCE_MINE, 0, LANDING_RADIUS) ?: RUNE_ESSENCE_MINE

        fun randomizeLocation(base: CoordGrid, random: GameRandom): CoordGrid {
            if (random.of(0..1) == 0) {
                return base
            }
            return CoordGrid(x = base.x + random.of(-1..1), z = base.z + random.of(-1..1))
        }

        private fun curseTravel(
            source: CoordGrid,
            target: Player,
            startTime: Int,
            endTime: Int,
            startHeight: Int,
            endHeight: Int,
        ): ProjAnim =
            ProjAnim(
                spotanim = SPOTANIM_CURSE_TRAVEL.asRSCM(RSCMType.SPOTANIM),
                startHeight = startHeight,
                endHeight = endHeight,
                startTime = startTime,
                endTime = endTime,
                angle = 16,
                progress = 128,
                sourceIndex = 0,
                targetIndex = -(target.slotId + 1),
                startCoord = source,
                endCoord = target.coords,
            )

        fun curseTravelInbound(source: CoordGrid, target: Player): ProjAnim =
            curseTravel(
                source = source,
                target = target,
                startTime = 61,
                endTime = 100,
                startHeight = 124,
                endHeight = 124,
            )

        fun curseTravelOutbound(source: CoordGrid, target: Player): ProjAnim =
            curseTravel(
                source = source,
                target = target,
                startTime = 0,
                endTime = 30,
                startHeight = 62,
                endHeight = 124,
            )

        private const val SPOTANIM_CURSE_TRAVEL = "spotanim.curse_travel"

        // Spotanim delays are client cycles, passed through without tick conversion.
        const val INBOUND_IMPACT_DELAY = 100

        const val OUTBOUND_IMPACT_DELAY = 30
    }
}

suspend fun ProtectedAccess.teleportToRuneEssenceMine(
    worldRepo: WorldRepository,
    npc: Npc,
    teleporter: EssenceMineTeleporter,
) {
    startDialogue(npc) { teleportToRuneEssenceMine(worldRepo, teleporter) }
}

suspend fun ProtectedAccess.teleportToMainLand(worldRepo: WorldRepository, portal: CoordGrid) {
    startDialogue { teleportToMainLand(worldRepo, portal) }
}

suspend fun Dialogue.teleportToRuneEssenceMine(
    worldRepo: WorldRepository,
    teleporter: EssenceMineTeleporter,
) {
    val npc = checkNotNull(npc) { "Rune essence mine teleport requires an npc dialogue context." }
    val dest = EssencePortals.randomEssenceMineDest(access)

    VarPlayerIntMapSetter.set(player, PORTAL_VARBIT, teleporter.portalVar)

    npc.say("Seventior Disthine Molenko!")
    npc.spotanim("spotanim.curse_casting", height = 92)
    worldRepo.soundArea(npc.coords, "synth.CURSE_CAST_AND_FIRE", radius = 10)

    worldRepo.projAnim(EssencePortals.curseTravelInbound(npc.coords, player))

    access.spotanim(
        "spotanim.curse_impact",
        delay = EssencePortals.INBOUND_IMPACT_DELAY,
        height = 124,
    )
    worldRepo.soundArea(
        player.coords,
        "synth.CURSE_HIT",
        delay = EssencePortals.INBOUND_IMPACT_DELAY,
        radius = 10,
    )

    delay(4)
    access.telejump(dest)
    if (teleporter == EssenceMineTeleporter.Sedridor) {
        access.publish(PlayerActionCompleteEvent(player, PlayerAction.EssenceMineTeleport, "npc.head_wizard_1op"))
    }
}

suspend fun Dialogue.teleportToMainLand(worldRepo: WorldRepository, portal: CoordGrid) {
    access.spam("You step through the portal...")

    access.spotanim(
        "spotanim.curse_impact",
        delay = EssencePortals.OUTBOUND_IMPACT_DELAY,
        height = 124,
    )
    worldRepo.projAnim(EssencePortals.curseTravelOutbound(portal, player))

    delay(1)
    val portalVar = player.vars[PORTAL_VARBIT]
    val teleporter =
        EssenceMineTeleporter.fromPortalVar(portalVar) ?: EssenceMineTeleporter.Sedridor
    access.telejump(teleporter.returnDest(access.random))
    player.soundSynth("synth.TELEPORT_ALL")
}
