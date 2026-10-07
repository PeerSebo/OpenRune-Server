package org.rsmod.content.bosses.wildernessarchaeologists

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.EffectInterpreter
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WildernessArchaeologists @Inject internal constructor(
    private val deps: BossDeps,
    private val disarm: FanaticDisarm,
) : PluginScript() {
    private val lives = IdentityHashMap<Npc, Any>()
    private val volleys = GroundVolley(deps.worldQueues)
    private val archaeologistId by lazy { ARCHAELOGIST.asRSCM(RSCMType.NPC) }
    private val fanaticId by lazy { FANATIC.asRSCM(RSCMType.NPC) }

    override fun ScriptContext.startup() {
        BossCombat.register(this, archaeologistSpec, deps)
        BossCombat.register(this, fanaticSpec, deps)
        deps.extensionRegistry.register(ARCH_ATTACK) { access, npc, target, _ ->
            attackArchaeologist(checkNotNull(access), npc, target)
        }
        deps.extensionRegistry.register(FANATIC_ATTACK) { access, npc, target, _ ->
            attackFanatic(checkNotNull(access), npc, target)
        }
        for (id in listOf(archaeologistId, fanaticId)) {
            onEvent<NpcStateEvents.Spawn>(id) { reset(npc) }
        }
        onEvent<NpcStateEvents.Respawn> { if (owns(npc)) reset(npc) }
        onEvent<NpcStateEvents.Delete> { if (owns(npc)) lives.remove(npc) }
    }

    private fun owns(npc: Npc) = npc.id == archaeologistId || npc.id == fanaticId

    private fun reset(npc: Npc) {
        lives[npc] = Any()
        npc.apRangeOverride = ATTACK_RANGE
    }

    private fun attackArchaeologist(access: StandardNpcAccess, npc: Npc, target: Player) {
        if (!target.isValidTarget() || target.level != npc.level) return
        val special = deps.random.of(5) == 0
        if (special) {
            access.say("Rain of knowledge!")
            access.anim("seq.book_chuck")
            bombard(npc, target, BOOK_SPECIAL, BOOK_IMPACT, 24)
            return
        }
        access.say(deps.random.pick(ARCH_QUOTES))
        val melee = npc.isWithinDistance(target, 1) && deps.random.of(2) == 0
        val effect = if (melee) ARCH_MELEE else ARCH_RANGED
        EffectInterpreter(npc, target, archaeologistSpec, deps.encounterRegistry.of(npc), deps)
            .run(access, effect)
    }

    private fun attackFanatic(access: StandardNpcAccess, npc: Npc, target: Player) {
        if (!target.isValidTarget() || target.level != npc.level) return
        access.say(deps.random.pick(FANATIC_QUOTES))
        access.anim("seq.human_casting")
        when (deps.random.of(10)) {
            0, 1 -> bombard(npc, target, FANATIC_GREEN, FANATIC_GREEN_IMPACT, 31)
            2 -> {
                EffectInterpreter(npc, target, fanaticSpec, deps.encounterRegistry.of(npc), deps)
                    .run(access, FANATIC_DISARM)
                val life = lives.getOrPut(npc) { Any() }
                val uid = target.uid
                disarm.schedule(deps.worldQueues, 3, deps.random,
                    active = { lives[npc] === life && npc.hitpoints > 0 && npc.isVisible && npc.isSlotAssigned },
                    victim = {
                        uid.resolve(deps.playerList)?.takeIf {
                            it.isValidTarget() && it.level == npc.level &&
                                npc.isWithinDistance(it, ATTACK_RANGE + 3)
                        }
                    },
                )
            }
            else -> EffectInterpreter(npc, target, fanaticSpec, deps.encounterRegistry.of(npc), deps)
                .run(access, FANATIC_MAGIC)
        }
    }

    private fun bombard(npc: Npc, target: Player, projectile: String, impact: String, maxHit: Int) {
        val life = lives.getOrPut(npc) { Any() }
        val uid = target.uid
        val centres = GroundVolley.scatter(target.coords, deps.random)
        val spot = projectile.asRSCM(RSCMType.SPOTANIM)
        // Coordinate projectiles stay at their landing tiles so movement can avoid the blast.
        centres.forEach { tile ->
            deps.bossProjectile(spot, npc.coords, tile, 40, 0, 20, 70, 45)
        }
        volleys.launch(
            centres = centres,
            delay = 4,
            radius = 1,
            active = { lives[npc] === life && npc.hitpoints > 0 && npc.isVisible && npc.isSlotAssigned },
            victimPosition = { uid.resolve(deps.playerList)?.takeIf { it.isValidTarget() }?.coords },
            impact = { deps.worldRepo.spotanimMap(SpotanimType(impact.asRSCM(RSCMType.SPOTANIM)), it) },
            hit = {
                uid.resolve(deps.playerList)?.let {
                    it.finishNpcHit(npc, 1, HitType.Typeless, deps.random.of(maxHit + 1), deps.playerHitModifier)
                }
            },
        )
    }

    companion object {
        const val ARCHAELOGIST = "npc.crazy_archaeologist"
        const val FANATIC = "npc.chaos_fanatic"
        const val ATTACK_RANGE = 7
        private const val ARCH_ATTACK = "wilderness_archaeologists.archaeologist_attack"
        private const val FANATIC_ATTACK = "wilderness_archaeologists.fanatic_attack"
        private const val BOOK_SPECIAL = "spotanim.crazy_archaeologist_book_special"
        private const val BOOK_IMPACT = "spotanim.firewave_impact"
        private const val FANATIC_GREEN = "spotanim.chaoselemental_spotanim_madness_travel"
        private const val FANATIC_GREEN_IMPACT = "spotanim.chaoselemental_spotanim_madness_impact"

        val archaeologistSpec: BossSpec = boss(ARCHAELOGIST) {
            stats(attackRate = 3)
            val attack = ability("attack") { include(external(ARCH_ATTACK)) }
            phase("combat") { rotationSelector { +then(attack) } }
        }
        val fanaticSpec: BossSpec = boss(FANATIC) {
            stats(attackRate = 2)
            val attack = ability("attack") { include(external(FANATIC_ATTACK)) }
            phase("combat") { rotationSelector { +then(attack) } }
        }

        private val ARCH_MELEE = sequence(
            anim("seq.human_unarmedpunch"),
            hit(Roll(0..14).withAccuracy(), Melee),
        )
        private val ARCH_RANGED = sequence(
            anim("seq.book_chuck"),
            projectile(
                "spotanim.crazy_archaeologist_book",
                config = ProjectileConfig(startDelay = 20, travelTime = 30, stepMultiplier = 3),
                hit = Effect.Hit(damage = Roll(0..14).withAccuracy(), type = Ranged),
            ),
        )
        private val FANATIC_MAGIC = projectile(
            "spotanim.chaoselemental_spotanim_confusion_travel",
            config = ProjectileConfig(startDelay = 10, travelTime = 30, stepMultiplier = 3),
            hit = Effect.Hit(damage = Roll(0..31).withAccuracy(), type = Magic,
                spotanim = "spotanim.chaoselemental_spotanim_confusion_impact"),
        )
        private val FANATIC_DISARM = projectile(
            "spotanim.chaoselemental_spotanim_discord_travel",
            config = ProjectileConfig(startDelay = 10, travelTime = 65, stepMultiplier = 0),
        )
        private val ARCH_QUOTES = listOf(
            "I'm Bellock - respect me!", "Get off my site!", "No-one messes with Bellock's dig!",
            "These ruins are mine!", "Taste my knowledge!", "You belong in a museum!",
        )
        private val FANATIC_QUOTES = listOf(
            "BURN!", "WEUGH!", "Devilish Oxen Roll!!", "All your wilderness are belong to them!",
            "AhehHeheuhHhahueHuUEehEahAH!", "I shall call him squidgy and he shall be my squidgy!!",
        )
    }
}
