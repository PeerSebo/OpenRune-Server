package org.rsmod.content.other.consumables.food

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.other.consumables.potion.PotionLifecycleFixture

@ResourceLock("ServerCacheManager")
class DelayedFoodStateTest {
    @Test
    fun `all hunter meats retain original immediate heal delayed heal and seven tick timing`() {
        for ((name, immediate, delayed) in listOf(
            Triple("cooked_wild_kebbit", 4, 4),
            Triple("cooked_larupia", 6, 5),
            Triple("cooked_barb_tailed_kebbit", 7, 5),
            Triple("cooked_graahk", 8, 6),
            Triple("cooked_kyatt", 9, 8),
            Triple("cooked_fennec_fox", 11, 8),
            Triple("cooked_dashing_kebbit", 13, 10),
            Triple("cooked_sunlight_antelope", 12, 9),
            Triple("cooked_moonlight_antelope", 14, 12),
        )) {
            val f = PotionLifecycleFixture()
            val service = f.injector.getInstance(FoodSpecialEffectService::class.java)
            f.level("stat.hitpoints", 40)
            val outcome = consume(f, service, name)
            assertEquals(immediate, outcome.healAmount, name)
            assertEquals(delayed, outcome.delayedHealAmount, name)
            assertEquals(1007, f.timer(FoodSpecialEffectService.DELAYED_HEAL_TIMER), name)
            f.at(1006)
            service.processDelayedHeals(f.access())
            assertEquals(40, f.player.stat("stat.hitpoints"), name)
            assertEquals(1007, f.timer(FoodSpecialEffectService.DELAYED_HEAL_TIMER), name)
            f.at(1007)
            service.processDelayedHeals(f.access())
            assertEquals(40 + delayed, f.player.stat("stat.hitpoints"), name)
            assertNull(FoodSpecialState.pending(f.player), name)
            assertNull(f.timer(FoodSpecialEffectService.DELAYED_HEAL_TIMER), name)
            service.processDelayedHeals(f.access())
            assertEquals(40 + delayed, f.player.stat("stat.hitpoints"), name)
        }
    }

    @Test
    fun `a later hunter meal replaces the heal amount effect and due time`() {
        val f = PotionLifecycleFixture()
        val service = f.injector.getInstance(FoodSpecialEffectService::class.java)
        f.level("stat.hitpoints", 40)
        f.player.runEnergy = 3000
        consume(f, service, "cooked_dashing_kebbit")
        f.at(1003)
        consume(f, service, "cooked_larupia")
        f.at(1007)
        service.processDelayedHeals(f.access())
        assertEquals(40, f.player.stat("stat.hitpoints"))
        assertEquals(1010, f.timer(FoodSpecialEffectService.DELAYED_HEAL_TIMER))
        f.at(1010)
        service.processDelayedHeals(f.access())
        assertEquals(45, f.player.stat("stat.hitpoints"))
        assertEquals(3000, f.player.runEnergy)
    }

    @Test
    fun `ordinary food and potions leave a pending hunter meal intact`() {
        val f = PotionLifecycleFixture()
        val service = f.injector.getInstance(FoodSpecialEffectService::class.java)
        consume(f, service, "cooked_graahk")
        val pending = FoodSpecialState.pending(f.player)
        f.at(1003)
        consume(f, service, "strawberry")
        f.drink("dbrow.effect_stamina")
        assertEquals(pending, FoodSpecialState.pending(f.player))
        assertEquals(1007, f.timer(FoodSpecialEffectService.DELAYED_HEAL_TIMER))
    }

    @Test
    fun `delayed healing caps at base and preserves existing overheal`() {
        for (level in listOf(95, 99, 110)) {
            val f = PotionLifecycleFixture()
            val service = f.injector.getInstance(FoodSpecialEffectService::class.java)
            f.level("stat.hitpoints", level)
            consume(f, service, "cooked_moonlight_antelope")
            f.at(1007)
            service.processDelayedHeals(f.access())
            assertEquals(maxOf(99, level), f.player.stat("stat.hitpoints"))
        }
    }

    @Test
    fun `dashing kebbit restores ten percent energy even without missing hitpoints`() {
        for ((before, after) in listOf(3000 to 4000, 9700 to 10000)) {
            val f = PotionLifecycleFixture()
            val service = f.injector.getInstance(FoodSpecialEffectService::class.java)
            f.player.runEnergy = before
            consume(f, service, "cooked_dashing_kebbit")
            assertEquals(before, f.player.runEnergy)
            f.at(1007)
            service.processDelayedHeals(f.access())
            assertEquals(after, f.player.runEnergy)
            assertEquals(99, f.player.stat("stat.hitpoints"))
        }
    }

    @Test
    fun `moonlight antelope cures poison or reduces venom exactly once`() {
        for (venom in listOf(false, true)) {
            val f = PotionLifecycleFixture()
            val service = f.injector.getInstance(FoodSpecialEffectService::class.java)
            VarPlayerIntMapSetter.set(f.player, if (venom) "varp.venom_strikes" else "varp.poison_severity", 3)
            consume(f, service, "cooked_moonlight_antelope")
            f.at(1007)
            service.processDelayedHeals(f.access())
            assertFalse(PlayerVenom.isEnvenomed(f.player))
            assertEquals(venom, PlayerPoison.isPoisoned(f.player))
            service.processDelayedHeals(f.access())
            assertEquals(venom, PlayerPoison.isPoisoned(f.player))
        }
    }

    @Test
    fun `death cancels pending healing and its secondary effect through the registered hook`() {
        val f = PotionLifecycleFixture()
        val service = f.injector.getInstance(FoodSpecialEffectService::class.java)
        f.level("stat.hitpoints", 40)
        f.player.runEnergy = 3000
        consume(f, service, "cooked_dashing_kebbit")
        f.death()
        assertNull(FoodSpecialState.pending(f.player))
        assertNull(f.timer(FoodSpecialEffectService.DELAYED_HEAL_TIMER))
        assertTrue(FIELDS.all { f.player.vars[it] == 0 })
        f.at(1007)
        service.processDelayedHeals(f.access())
        assertEquals(40, f.player.stat("stat.hitpoints"))
        assertEquals(3000, f.player.runEnergy)
    }

    @Test
    fun `reconnecting discards delayed healing and every component has temporary private scope`() {
        val f = PotionLifecycleFixture()
        val service = f.injector.getInstance(FoodSpecialEffectService::class.java)
        consume(f, service, "cooked_dashing_kebbit")
        val saved = f.saved()
        for (field in FIELDS) {
            assertFalse(field.asRSCM() in saved.vars)
            val definition = ServerCacheManager.getVarp(field.asRSCM())!!
            assertEquals(VarpLifetime.Temp, definition.scope)
            assertEquals(VarpTransmitLevel.Never, definition.transmit)
        }
        f.restore(saved, 9000)
        assertNull(FoodSpecialState.pending(f.player))
        assertNull(f.timer(FoodSpecialEffectService.DELAYED_HEAL_TIMER))
    }

    private fun consume(f: PotionLifecycleFixture, service: FoodSpecialEffectService, name: String): FoodSpecialOutcome {
        val outcome = service.prepare(f.access(), name, defaultHeal = 0, defaultCanOverheal = false)
        service.applyAfterConsume(f.access(), name, outcome)
        return outcome
    }

    companion object {
        private val FIELDS = listOf("due_at", "amount", "effect")
            .map { "varp.consumables_hunter_heal_$it" }

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
