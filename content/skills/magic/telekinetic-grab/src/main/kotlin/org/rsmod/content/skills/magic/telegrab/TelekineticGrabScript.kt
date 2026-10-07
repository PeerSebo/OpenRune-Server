package org.rsmod.content.skills.magic.telegrab

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.events.interact.ObjTDefaultEvents
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpObjT
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.game.obj.Obj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TelekineticGrabScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val takeValidator: PlayerObjTakeValidator,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val spell = resolveSpell()
        if (spell == null) {
            logger.warn { "Telekinetic Grab spell not found in spellbook registry; not binding." }
            return
        }
        val component = TelegrabComponent.asRSCM(RSCMType.COMPONENT)
        onProtectedEvent<ObjTDefaultEvents.Ap>(component) { castTelegrab(spell, it.obj) }
        onOpObjT(TelegrabComponent) { castTelegrab(spell, it.obj) }
    }

    private fun resolveSpell(): MagicSpell? {
        val type = ServerCacheManager.getItem(SpellObj.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(type)
    }

    internal suspend fun ProtectedAccess.castTelegrab(spell: MagicSpell, obj: Obj) {
        if (!canTake(obj)) return
        val result = runes.attemptCast(player, spell)
        if (result.isFailure()) {
            return
        }

        val distance = player.coords.chebyshevDistance(obj.coords)
        val flightTime = ProjStartTime + ProjLengthAdjustment + ProjStepMultiplier * distance

        faceSquare(obj.coords)
        anim(SeqCast)
        spotanim(SpotanimCast, height = CastSpotanimHeight)
        soundSynth(SynthCast)
        worldRepo.projAnim(
            ProjAnim(
                spotanim = travelSpotanim,
                startHeight = ProjStartHeight,
                endHeight = ProjEndHeight,
                startTime = ProjStartTime,
                endTime = flightTime,
                angle = ProjAngle,
                progress = ProjProgress,
                // Anchor both projectile ends to tiles so it does not track the caster.
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = player.coords,
                endCoord = obj.coords,
            )
        )
        spotanimMap(worldRepo, SpotanimImpact, obj.coords, height = ImpactHeight, delay = flightTime)
        worldRepo.soundArea(
            obj.coords,
            SynthImpact,
            delay = ImpactSoundDelay,
            radius = ImpactSoundRadius,
        )
        statAdvance("stat.magic", spell.castXp)

        delay(DeliveryTicks)
        deliver(obj)
    }

    // Revalidate existence and visibility at delivery; the object may have been taken or expired.
    internal fun ProtectedAccess.deliver(obj: Obj) {
        val ground =
            objRepo.findAll(obj.coords).firstOrNull {
                it.type == obj.type && it.isVisibleTo(player)
            } ?: return
        if (!canTake(ground)) return
        val internal = RSCM.getReverseMapping(RSCMType.OBJ, ground.type)
        if (invAdd(inv, internal, ground.count).failure) {
            // TODO: verify the full-inventory response; leave the object in place.
            return
        }
        objRepo.del(ground)
    }

    private fun ProtectedAccess.canTake(obj: Obj): Boolean {
        val type = ServerCacheManager.getItem(obj.type) ?: return false
        val denial = takeValidator.validate(player, obj, type)
        if (denial != null) {
            mes(denial)
            return false
        }
        return true
    }

    private val travelSpotanim: Int by lazy { SpotanimTravel.asRSCM(RSCMType.SPOTANIM) }

    private companion object {
        private val logger = InlineLogger()

        private const val SpellObj = "obj.33_tele_grab"

        private const val TelegrabComponent = "component.magic_spellbook:telegrab"

        private const val SeqCast = "seq.human_casttelegrab"

        private const val SpotanimCast = "spotanim.telegrab_casting"

        private const val CastSpotanimHeight = 92

        private const val SpotanimTravel = "spotanim.telegrab_travel"

        private const val SpotanimImpact = "spotanim.telegrab_impact"

        private const val ImpactHeight = 20

        private const val SynthCast = "synth.TELEGRAB_CAST_AND_FIRE"

        private const val SynthImpact = "synth.TELEGRAB_HIT"

        private const val ImpactSoundDelay = 3

        private const val ImpactSoundRadius = 10

        private const val ProjStartHeight = 140

        private const val ProjEndHeight = 0

        private const val ProjStartTime = 48

        private const val ProjLengthAdjustment = 12

        private const val ProjStepMultiplier = 10

        private const val ProjAngle = 16

        private const val ProjProgress = 64

        private const val DeliveryTicks = 3
    }
}
