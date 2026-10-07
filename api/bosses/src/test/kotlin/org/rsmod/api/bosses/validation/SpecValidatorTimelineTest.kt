package org.rsmod.api.bosses.validation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.BossStats
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.PhaseSpec
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.TargetExpr

class SpecValidatorTimelineTest {
    @Test
    fun `positive waits and zero repeats are valid in a sequence`() {
        assertTrue(errors(Effect.Sequence(listOf(Effect.Wait(1), Effect.Repeat(0..0, Effect.NoOp)))).isEmpty())
    }

    @Test
    fun `wait and legacy delay must advance time`() {
        for (effect in listOf(Effect.Wait(0), Effect.Wait(-1), Effect.Delay(0), Effect.Delay(-1))) {
            assertTrue(errors(effect).any { "greater than 0" in it }, "Expected invalid duration: $effect")
        }
    }

    @Test
    fun `parallel rejects direct waits but accepts independently timed sequences`() {
        for (wait in listOf(Effect.Wait(1), Effect.Delay(1))) {
            assertTrue(errors(Effect.Parallel(listOf(wait))).any { "Parallel cannot contain" in it })
            assertTrue(errors(Effect.Parallel(listOf(Effect.Sequence(listOf(wait, Effect.NoOp))))).isEmpty())
        }
    }

    @Test
    fun `repeat rejects negative or empty counts and negative gaps`() {
        for (range in listOf(-1..2, 3..2)) {
            assertTrue(errors(Effect.Repeat(range, Effect.NoOp)).any { "Repeat times" in it })
        }
        assertTrue(errors(Effect.Repeat(2..2, Effect.NoOp, gap = -1)).any { "gap" in it.lowercase() })
    }

    @Test
    fun `impact tile is rejected outside impact callbacks including nested target expressions`() {
        val targets = listOf(
            TargetExpr.ImpactTile,
            TargetExpr.RandomWalkableTile(2, TargetExpr.ImpactTile),
            TargetExpr.AllInRadius(2, TargetExpr.RandomWalkableTile(1, TargetExpr.ImpactTile)),
        )
        for (target in targets) {
            assertTrue(errors(Effect.OnEach(target, Effect.NoOp)).any { "outside" in it && "ImpactTile" in it })
        }
    }

    @Test
    fun `impact context propagates through nested timed conditional and selection branches`() {
        val effect = Effect.Projectile(
            spotanim = "spotanim.test_projectile",
            onImpact = Effect.Sequence(listOf(
                Effect.Wait(2),
                Effect.Whenever(Condition.Always, Effect.Choose(
                    Selector.Rotation(listOf("impact")),
                    mapOf("impact" to Effect.OnEach(TargetExpr.AllInRadius(2, TargetExpr.ImpactTile), Effect.NoOp)),
                )),
            )),
        )
        assertTrue(errors(effect).isEmpty())
    }

    @Test
    fun `references nested inside projectile callbacks are validated`() {
        val effect = Effect.Projectile(
            spotanim = "spotanim.test_projectile",
            onImpact = Effect.Sequence(listOf(Effect.Wait(1), Effect.Run("missing"), Effect.TransitionTo("missing"))),
        )
        val errors = errors(effect)
        assertTrue(errors.any { "Run references ability 'missing'" in it })
        assertTrue(errors.any { "TransitionTo references phase 'missing'" in it })
    }

    @Test
    fun `legacy area centre respects impact tile scope`() {
        val area = Effect.TileAoE(
            center = TargetExpr.ImpactTile,
            radius = 1,
            damage = DamageExpr.Fixed(5),
            type = HitType.Typeless,
        )
        assertTrue(errors(area).any { "outside" in it && "ImpactTile" in it })
        assertTrue(errors(Effect.Projectile("spotanim.test_projectile", onImpact = area)).isEmpty())
    }

    private fun errors(effect: Effect): List<String> = SpecValidator.validate(
        BossSpec(
            npcTypes = listOf("npc.test_boss"),
            stats = BossStats(),
            abilities = mapOf("attack" to effect),
            phases = mapOf("main" to PhaseSpec("main", selector = Selector.Rotation(listOf("attack")))),
            triggers = emptyList(),
        ),
    ).map { it.message }
}
