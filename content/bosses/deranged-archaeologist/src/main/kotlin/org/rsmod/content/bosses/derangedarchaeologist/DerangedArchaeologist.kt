package org.rsmod.content.bosses.derangedarchaeologist

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.ScriptContext

class DerangedArchaeologist @Inject constructor(
    deps: BossDeps,
    private val hitProcessor: InstantPlayerHitProcessor,
) : BossPluginScript(deps) {
    private val nextSpecial = IdentityHashMap<Npc, Int>()
    private val books = ArchaeologistBooks(deps.random, deps.worldQueues)

    override val spec = boss("npc.fossil_crazy_archaeologist") {
        stats(attackRate = 3)
        ability("punch") {
            anim("seq.human_unarmedpunch")
            hit { damage(0..25).rollWithAccuracy(); type(Melee) }
        }
        ability("book") {
            anim("seq.book_chuck")
            projectile(
                spotanim = "spotanim.crazy_archaeologist_book",
                hit = Effect.Hit(damage = Roll(0..25).withAccuracy(), type = Ranged),
            )
        }
        val attack = ability("attack") { include(external("deranged.attack")) }
        phase("combat") { rotationSelector { +then(attack) } }
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        onEvent<NpcStateEvents.Delete> { nextSpecial.remove(npc) }
        onEvent<NpcStateEvents.Respawn> { nextSpecial.remove(npc) }
        deps.extensionRegistry.register("deranged.attack") { access, npc, target, _ ->
            checkNotNull(access)
            val now = deps.mapClock.cycle
            if (now >= (nextSpecial[npc] ?: Int.MIN_VALUE) && deps.random.of(3) == 0) {
                nextSpecial[npc] = now + SPECIAL_COOLDOWN
                access.say("Learn to Read!")
                access.anim("seq.book_chuck")
                books.throwBooks(
                    npc.coords,
                    target.coords,
                    launch = { source, destination, ticks ->
                        deps.bossProjectile(
                            "spotanim.crazy_archaeologist_book_special".asRSCM(RSCMType.SPOTANIM),
                            source, destination, 40, 0, 0, ticks * 30, 32,
                        )
                    },
                    impact = { tile ->
                        deps.playerList.filter { it.isValidTarget() }.forEach { player ->
                            val max = ArchaeologistBooks.maxDamage(player.coords, tile)
                            if (max > 0) player.takeInstantHit(
                                HitType.Typeless, deps.random.of(max + 1), hitProcessor,
                            )
                        }
                    },
                )
            } else {
                val melee = npc.coords.level == target.coords.level &&
                    npc.coords.chebyshevDistance(target.coords) <= 1 && deps.random.of(2) == 0
                val effect = checkNotNull(spec.abilities[if (melee) "punch" else "book"])
                EffectInterpreter(npc, target, spec, deps.encounter(npc), deps).run(access, effect)
            }
        }
    }

    internal companion object { const val SPECIAL_COOLDOWN = 25 }
}
