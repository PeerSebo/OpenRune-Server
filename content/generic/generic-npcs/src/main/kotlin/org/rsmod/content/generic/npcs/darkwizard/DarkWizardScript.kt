package org.rsmod.content.generic.npcs.darkwizard

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isInPvnCombat
import org.rsmod.api.player.isInPvpCombat
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onPlayerSoftQueueWithArgs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DarkWizardScript
@Inject
constructor(
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val worldRepo: WorldRepository,
    private val hitModifier: PlayerHitModifier,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (npc in YOUNG_WIZARDS) {
            register(npc, WATER_STRIKE, CONFUSE)
        }
        for (npc in OLD_WIZARDS) {
            register(npc, EARTH_STRIKE, WEAKEN)
        }
        // Use a soft queue so an already-launched spell lands during unrelated player actions.
        onPlayerSoftQueueWithArgs<CurseLanding>(CURSE_QUEUE) { player.applyCurse(args) }
    }

    private fun Player.applyCurse(landing: CurseLanding) {
        if (stat(landing.stat) >= statBase(landing.stat)) {
            statSub(landing.stat, constant = 0, percent = CURSE_DRAIN_PERCENT)
        }
    }

    private fun ScriptContext.register(npc: String, strike: StrikeSpell, curse: CurseSpell) {
        val type = ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $npc")
        onAiOpPlayer2(type) { attack(it.target, strike, curse) }
        onAiApPlayer2(type) { attack(it.target, strike, curse) }
    }

    private fun StandardNpcAccess.attack(target: Player, strike: StrikeSpell, curse: CurseSpell) {
        if (!canAttack(target)) {
            resetMode()
            return
        }
        if (actionDelay > mapClock) {
            return
        }
        if (!npc.isInCombat()) {
            resetMode()
            return
        }
        actionDelay = mapClock + npc.attackRate()
        setAttackVars(target)

        if (random.of(CURSE_ONE_IN) == 0) {
            castCurse(target, curse)
        } else {
            castStrike(target, strike)
        }
    }

    private fun StandardNpcAccess.castStrike(target: Player, spell: StrikeSpell) {
        val proj = cast(target, spell)
        val landed = accuracy.rollMagicAccuracy(npc, target, random)
        if (!landed) {
            splash(target, proj)
            return
        }
        val maxHit = maxHits.getMagicMaxHit(npc, target).coerceIn(1, spell.maxHit)
        val damage = random.of(0..maxHit)
        target.spotanim(spell.impact, delay = proj.clientCycles, height = IMPACT_HEIGHT)
        worldRepo.soundArea(target, spell.hitSound, delay = proj.clientCycles, radius = SOUND_RADIUS)
        target.finishNpcHit(npc, proj.serverCycles, HitType.Magic, damage, hitModifier, proj.clientCycles)
    }

    private fun StandardNpcAccess.castCurse(target: Player, spell: CurseSpell) {
        val proj = cast(target, spell)
        val landed = accuracy.rollMagicAccuracy(npc, target, random)
        if (!landed) {
            splash(target, proj)
            return
        }
        target.spotanim(spell.impact, delay = proj.clientCycles, height = IMPACT_HEIGHT)
        spell.hitSound?.let {
            worldRepo.soundArea(target, it, delay = proj.clientCycles, radius = SOUND_RADIUS)
        }
        target.queueCombatRetaliate(npc, delay = proj.serverCycles)
        target.softQueue(CURSE_QUEUE, proj.serverCycles, CurseLanding(spell.stat))
    }

    private fun StandardNpcAccess.cast(target: Player, spell: Spell): ProjAnim {
        anim(CAST_ANIM)
        spotanim(spell.casting, height = CAST_HEIGHT)
        worldRepo.soundArea(npc, spell.castSound, radius = SOUND_RADIUS)
        val projType =
            ServerCacheManager.getProjectile(spell.projanim.asRSCM(RSCMType.PROJANIM))
                ?: error("Missing projanim: ${spell.projanim}")
        val proj = ProjAnim.fromNpcToPlayer(npc, target, spell.travel.asRSCM(RSCMType.SPOTANIM), projType)
        worldRepo.projAnim(proj)
        return proj
    }

    private fun StandardNpcAccess.splash(target: Player, proj: ProjAnim) {
        target.spotanim(SPLASH_SPOT, delay = proj.clientCycles, height = IMPACT_HEIGHT)
        worldRepo.soundArea(target, SPLASH_SOUND, delay = proj.clientCycles, radius = SOUND_RADIUS)
        target.queueCombatRetaliate(npc, delay = proj.serverCycles)
    }

    private fun StandardNpcAccess.canAttack(target: Player): Boolean {
        if (!target.isValidTarget()) {
            return false
        }
        val singleCombat = !mapMultiway(areaChecker)
        if (singleCombat) {
            if (target.isInPvpCombat()) {
                return false
            }
            if (target.isInPvnCombat()) {
                val aggressor = target.aggressiveNpc
                if (aggressor != null && aggressor != npc.uid) {
                    return false
                }
            }
        }
        return true
    }

    private fun StandardNpcAccess.setAttackVars(target: Player) {
        npc.lastAttack = mapClock
        npc.attackingPlayer = target.uid
        target.lastCombat = mapClock
        target.aggressiveNpc = npc.uid
    }

    private data class CurseLanding(val stat: String)

    private sealed interface Spell {
        val casting: String
        val travel: String
        val impact: String
        val projanim: String
        val castSound: String
    }

    private data class StrikeSpell(
        override val casting: String,
        override val travel: String,
        override val impact: String,
        override val castSound: String,
        val hitSound: String,
        val maxHit: Int,
    ) : Spell {
        override val projanim: String = "projanim.magic_spell"
    }

    private data class CurseSpell(
        override val casting: String,
        override val travel: String,
        override val impact: String,
        override val castSound: String,
        val hitSound: String?,
        val stat: String,
    ) : Spell {
        override val projanim: String = "projanim.confuse"
    }

    private companion object {
        private const val CAST_ANIM = "seq.human_caststrike"
        private const val CAST_HEIGHT = 92
        private const val IMPACT_HEIGHT = 124
        private const val SOUND_RADIUS = 10
        private const val SPLASH_SPOT = "spotanim.failedspell_impact"
        private const val SPLASH_SOUND = "synth.SPELLFAIL"
        private const val CURSE_QUEUE = "queue.dark_wizard_curse"

        private const val CURSE_ONE_IN = 3

        private const val CURSE_DRAIN_PERCENT = 5

        private val WATER_STRIKE =
            StrikeSpell(
                casting = "spotanim.waterstrike_casting",
                travel = "spotanim.waterstrike_travel",
                impact = "spotanim.waterstrike_impact",
                castSound = "synth.WATERSTRIKE_CAST_AND_FIRE",
                hitSound = "synth.WATERSTRIKE_HIT",
                maxHit = 4,
            )

        private val EARTH_STRIKE =
            StrikeSpell(
                casting = "spotanim.earthstrike_casting",
                travel = "spotanim.earthstrike_travel",
                impact = "spotanim.earthstrike_impact",
                castSound = "synth.EARTHSTRIKE_CAST_AND_FIRE",
                hitSound = "synth.EARTHSTRIKE_HIT",
                maxHit = 6,
            )

        private val CONFUSE =
            CurseSpell(
                casting = "spotanim.confuse_casting",
                travel = "spotanim.confuse_travel",
                impact = "spotanim.confuse_impact",
                castSound = "synth.CONFUSE_CAST_AND_FIRE",
                hitSound = "synth.CONFUSE_HIT",
                stat = "stat.attack",
            )

        private val WEAKEN =
            CurseSpell(
                casting = "spotanim.weaken_casting",
                travel = "spotanim.weaken_travel",
                impact = "spotanim.weaken_impact",
                castSound = "synth.WEAKEN_ALL",
                hitSound = null,
                stat = "stat.strength",
            )

        private val YOUNG_WIZARDS =
            listOf(
                "npc.young_dark_wizard",
                "npc.fai_dark_wizard_young_1",
                "npc.fai_dark_wizard_young_2",
                "npc.qip_ds_young_dark_wizard1",
                "npc.qip_ds_young_dark_wizard2",
            )

        private val OLD_WIZARDS =
            listOf(
                "npc.bearded_dark_wizard",
                "npc.fai_dark_wizard_old_1",
                "npc.fai_dark_wizard_old_2",
                "npc.qip_ds_young_dark_wizard3",
                "npc.qip_ds_young_dark_wizard4",
            )

        private var Npc.lastAttack: Int by intVarn("varn.lastattack")
        private var Npc.attackingPlayer: PlayerUid? by typePlayerUidVarn("varn.attacking_player")
        private var Player.lastCombat: Int by intVarp("varp.lastcombat")
        private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp("varp.aggressive_npc")
    }
}
