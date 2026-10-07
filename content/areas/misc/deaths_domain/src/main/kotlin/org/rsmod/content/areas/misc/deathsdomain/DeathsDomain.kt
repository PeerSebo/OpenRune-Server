package org.rsmod.content.areas.misc.deathsdomain

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.nullableIntVarp
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DeathsDomain @Inject constructor(private val npcRepo: NpcRepository) : PluginScript() {
    private val logger = InlineLogger()

    private var death: Npc? = null
    private var Player.returnCoord by nullableIntVarp("varp.deaths_domain_return_coord")

    override fun ScriptContext.startup() {
        onGameStartup { spawnDeath() }
        onOpLoc1(GraveEntrance) { enterDomain() }
        onOpLoc1(ExitPortal) { leaveDomain() }
    }

    private suspend fun ProtectedAccess.enterDomain() {
        arriveDelay()

        player.returnCoord = player.coords.packed

        // Only one animation per tick survives; send the sequence before other presentation.
        anim(EnterSeq, delay = EnterSeqDelay)
        soundSynth(TransitionSound)
        // Clientscript 948 uses 255 for clear and 0 for opaque.
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FadeDuration,
        )

        delay(MoveDelay)
        telejump(OfficeArrival)
        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = FadeDuration,
        )

        delay(1)
        closeFadeOverlayNow()

        delay(GreetDelay)
        greetOnArrival()
    }

    private suspend fun ProtectedAccess.leaveDomain() {
        arriveDelay()
        soundSynth(TransitionSound)
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FadeDuration,
        )

        delay(MoveDelay)
        val stored = player.returnCoord
        telejump(if (stored != null) CoordGrid(stored) else GraveyardReturn)
        player.returnCoord = null
        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = FadeDuration,
        )

        delay(1)
        closeFadeOverlayNow()
    }

    private suspend fun ProtectedAccess.greetOnArrival() {
        val death = death ?: return
        startDialogue(death) {
            chatNpc(neutral, "Hello mortal. Have you come to retrieve something you lost when you died?")
        }
    }

    private fun spawnDeath() {
        val existing = npcRepo.findAll(ZoneKey.from(DeathCoord)).any { it.isType(DeathNpc) }
        if (existing) {
            logger.debug { "Death's Office already has a '$DeathNpc' spawn; not adding another." }
            return
        }
        val npc = Npc(DeathNpc, DeathCoord)
        npc.mode = NpcMode.None

        runCatching { npcRepo.add(npc, Int.MAX_VALUE) }
            .onSuccess { death = npc }
            .onFailure { logger.warn(it) { "Could not spawn '$DeathNpc' at $DeathCoord." } }
    }

    private companion object {
        private const val GraveEntrance = "loc.death_office_access_grave"

        private const val ExitPortal = "loc.death_office_exitportal"

        private const val DeathNpc = "npc.death_office_death"

        private const val EnterSeq = "seq.human_pickupfloor"

        private const val EnterSeqDelay = 20

        private const val TransitionSound = "synth.TOB_TRANSITION_CARD"

        private const val FadeDuration = 50

        private const val MoveDelay = 2

        private const val GreetDelay = 2

        private val OfficeArrival = CoordGrid(3171, 5726, 0)

        private val DeathCoord = CoordGrid(3179, 5726, 0)

        private val GraveyardReturn = CoordGrid(3238, 3194, 0)
    }
}
