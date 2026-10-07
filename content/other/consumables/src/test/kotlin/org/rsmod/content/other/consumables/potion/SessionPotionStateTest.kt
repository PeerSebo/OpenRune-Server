package org.rsmod.content.other.consumables.potion

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.constants
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.specials.energy.SpecialAttackEnergyModifier
import org.rsmod.content.other.consumables.potion.moons.MoonlightPotionEffect
import org.rsmod.content.other.consumables.potion.moons.moonlightExpiresAt
import org.rsmod.content.other.consumables.potion.toa.ToaLiquidAdrenalineEffect
import org.rsmod.content.other.consumables.potion.toa.ToaOverTimeEffect
import org.rsmod.content.other.consumables.potion.toa.ToaSmellingSaltsEffect
import org.rsmod.content.other.consumables.potion.toa.toaAdrenalineExpiresAt
import org.rsmod.content.other.consumables.potion.toa.toaAdrenalineWarned
import org.rsmod.content.other.consumables.potion.toa.toaSaltsExpiresAt
import org.rsmod.content.other.consumables.potion.toa.toaSaltsWarned

@ResourceLock("ServerCacheManager")
class SessionPotionStateTest {
    @Test
    fun `all seven fields are private session state excluded from saves`() {
        val f = PotionLifecycleFixture()
        activateAll(f)
        for (symbol in STATE_VARS) {
            val definition = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Temp, definition.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, definition.transmit, symbol)
            assertFalse(definition.id in f.saved().vars, symbol)
        }
        assertTrue(f.saved().attrs.isEmpty())
    }

    @Test
    fun `moonlight retains enhanced boost exact expiry and renewed deadline`() {
        val f = PotionLifecycleFixture()
        f.level("stat.prayer", 10)
        f.drink("dbrow.effect_moonlight_potion")
        assertEquals(118, f.player.stat("stat.attack"))
        assertEquals(118, f.player.stat("stat.strength"))
        assertEquals(125, f.player.stat("stat.defence"))
        assertEquals(46, f.player.stat("stat.prayer"))
        assertEquals(1500, f.player.moonlightExpiresAt)
        assertEquals(1500, f.timer(MoonlightPotionEffect.TIMER))
        assertEquals(500, f.player.vars[MOONLIGHT_DISPLAY])
        assertEquals(500, f.player.vars[DIVINE_DEFENCE_DISPLAY])

        f.at(1123)
        f.drink("dbrow.effect_moonlight_potion")
        f.at(1500)
        f.pulse(MoonlightPotionEffect.TIMER)
        assertEquals(1623, f.timer(MoonlightPotionEffect.TIMER))
        assertEquals(125, f.player.stat("stat.defence"))
        f.at(1623)
        f.pulse(MoonlightPotionEffect.TIMER)
        assertEquals(99, f.player.stat("stat.defence"))
        assertEquals(118, f.player.stat("stat.attack"))
        assertEquals(0, f.player.moonlightExpiresAt)
        assertEquals(0, f.player.vars[MOONLIGHT_DISPLAY])
        assertEquals(0, f.player.vars[DIVINE_DEFENCE_DISPLAY])
        assertNull(f.timer(MoonlightPotionEffect.TIMER))
    }

    @Test
    fun `low herblore moonlight does not schedule enhanced defence or erase an unrelated divine timer`() {
        val f = PotionLifecycleFixture()
        f.level("stat.herblore", 66)
        VarPlayerIntMapSetter.set(f.player, DIVINE_DEFENCE_DISPLAY, 100)
        f.drink("dbrow.effect_moonlight_potion")
        assertEquals(118, f.player.stat("stat.defence"))
        assertEquals(0, f.player.moonlightExpiresAt)
        assertNull(f.timer(MoonlightPotionEffect.TIMER))
        f.pulse(MoonlightPotionEffect.TIMER)
        assertEquals(100, f.player.vars[DIVINE_DEFENCE_DISPLAY])
        assertEquals(118, f.player.stat("stat.defence"))
    }

    @Test
    fun `adrenaline warns once at the correct boundary and renews its cost and display`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_toa_liquid_adrenaline")
        assertEquals(1250, f.player.toaAdrenalineExpiresAt)
        assertEquals(1233, f.timer(ToaLiquidAdrenalineEffect.TIMER))
        assertEquals(25, SpecialAttackEnergyModifier.adjustedCost(f.player, 50))
        assertBuff(f, 4375, 1250)
        f.at(1010)
        f.pulse(ToaLiquidAdrenalineEffect.TIMER)
        assertFalse(f.player.toaAdrenalineWarned)
        assertEquals(1233, f.timer(ToaLiquidAdrenalineEffect.TIMER))

        f.at(1233)
        f.pulse(ToaLiquidAdrenalineEffect.TIMER)
        f.at(1234)
        f.pulse(ToaLiquidAdrenalineEffect.TIMER)
        assertEquals(1, messages(f, "liquid adrenaline will wear off"))
        assertTrue(f.player.toaAdrenalineWarned)
        f.at(1240)
        f.drink("dbrow.effect_toa_liquid_adrenaline")
        assertFalse(f.player.toaAdrenalineWarned)
        assertEquals(1490, f.player.toaAdrenalineExpiresAt)
        assertBuff(f, 4375, 1490)
        f.at(1473)
        f.pulse(ToaLiquidAdrenalineEffect.TIMER)
        assertEquals(2, messages(f, "liquid adrenaline will wear off"))
        f.at(1490)
        f.pulse(ToaLiquidAdrenalineEffect.TIMER)
        assertEquals(50, SpecialAttackEnergyModifier.adjustedCost(f.player, 50))
        assertEquals(0, f.player.vars["varp.player_special_attack_cost_divisor"])
        assertEquals(0, f.player.vars[ADRENALINE_DISPLAY])
        assertFalse(f.player.toaAdrenalineWarned)
        assertNull(f.timer(ToaLiquidAdrenalineEffect.TIMER))
        assertBuff(f, 4375, 0)
        assertEquals(1, messages(f, "liquid adrenaline have worn off"))
    }

    @Test
    fun `salts refresh boosts and energy while native display rounds remaining time`() {
        val f = PotionLifecycleFixture()
        f.player.runEnergy = 0
        f.drink("dbrow.effect_toa_smelling_salts")
        assertEquals(1800, f.player.toaSaltsExpiresAt)
        assertEquals(32, f.player.vars[SALTS_DISPLAY])
        assertEquals(125, f.player.stat("stat.attack"))
        assertEquals(constants.run_max_energy / 4, f.player.runEnergy)
        assertBuff(f, 4376, 0)
        f.level("stat.attack", 1)
        f.at(1025)
        f.pulse(ToaSmellingSaltsEffect.TIMER)
        assertEquals(27, f.player.stat("stat.attack"))
        assertEquals(31, f.player.vars[SALTS_DISPLAY])
        assertEquals(constants.run_max_energy / 2, f.player.runEnergy)
        f.at(1775)
        f.pulse(ToaSmellingSaltsEffect.TIMER)
        assertEquals(1783, f.timer(ToaSmellingSaltsEffect.TIMER))
        f.at(1783)
        f.pulse(ToaSmellingSaltsEffect.TIMER)
        f.at(1784)
        f.pulse(ToaSmellingSaltsEffect.TIMER)
        assertEquals(1, messages(f, "smelling salts will wear off"))
        assertTrue(f.player.toaSaltsWarned)
        assertEquals(1, f.player.vars[SALTS_DISPLAY])
        f.at(1800)
        f.pulse(ToaSmellingSaltsEffect.TIMER)
        assertEquals(99, f.player.stat("stat.attack"))
        assertEquals(0, f.player.vars[SALTS_DISPLAY])
        assertEquals(0, f.player.toaSaltsExpiresAt)
        assertFalse(f.player.toaSaltsWarned)
        assertNull(f.timer(ToaSmellingSaltsEffect.TIMER))
        assertEquals(1, messages(f, "smelling salts have worn off"))
        assertTrue(f.client.messages.filterIsInstance<RunClientScript>().all { it.id == BUFF_SCRIPT.asRSCM() })
    }

    @Test
    fun `renewing salts restarts its full lifetime and warning while already at full display`() {
        val f = PotionLifecycleFixture()
        f.drink("dbrow.effect_toa_smelling_salts")
        f.at(1001)
        f.drink("dbrow.effect_toa_smelling_salts")
        assertEquals(1801, f.player.toaSaltsExpiresAt)
        assertEquals(32, f.player.vars[SALTS_DISPLAY])
        assertEquals(2, f.client.messages.filterIsInstance<RunClientScript>().size)
        f.at(1784)
        f.pulse(ToaSmellingSaltsEffect.TIMER)
        assertTrue(f.player.toaSaltsWarned)
        f.at(1790)
        f.drink("dbrow.effect_toa_smelling_salts")
        assertFalse(f.player.toaSaltsWarned)
        assertEquals(2590, f.player.toaSaltsExpiresAt)
        assertEquals(1815, f.timer(ToaSmellingSaltsEffect.TIMER))
    }

    @Test
    fun `silk provides exactly twenty heals and scarab exactly ten prayer restores`() {
        val f = PotionLifecycleFixture()
        f.player.statMap.setBaseLevel("stat.hitpoints", 120)
        f.level("stat.hitpoints", 1)
        f.level("stat.prayer", 0)
        f.drink("dbrow.effect_toa_silk_dressing")
        f.drink("dbrow.effect_toa_blessed_crystal_scarab")
        assertEquals(6, f.player.stat("stat.hitpoints"))
        assertEquals(8, f.player.stat("stat.prayer"))
        assertBuff(f, 4373, 1095)
        assertBuff(f, 4374, 1036)
        repeat(9) { i ->
            f.at(1000 + (i + 1) * 4)
            f.pulse(ToaOverTimeEffect.BLESSED_SCARAB_TIMER)
        }
        assertEquals(80, f.player.stat("stat.prayer"))
        assertNull(f.timer(ToaOverTimeEffect.BLESSED_SCARAB_TIMER))
        assertBuff(f, 4374, 0)
        repeat(19) { i ->
            f.at(1000 + (i + 1) * 5)
            f.pulse(ToaOverTimeEffect.SILK_DRESSING_TIMER)
        }
        assertEquals(101, f.player.stat("stat.hitpoints"))
        assertNull(f.timer(ToaOverTimeEffect.SILK_DRESSING_TIMER))
        assertBuff(f, 4373, 0)
        assertEquals(1, messages(f, "silk dressing have worn off"))
        assertEquals(1, messages(f, "blessed crystal scarab have worn off"))
        f.pulse(ToaOverTimeEffect.SILK_DRESSING_TIMER)
        f.pulse(ToaOverTimeEffect.BLESSED_SCARAB_TIMER)
        assertEquals(101, f.player.stat("stat.hitpoints"))
        assertEquals(80, f.player.stat("stat.prayer"))
    }

    @Test
    fun `renewing over time items resets phase remaining pulses and absolute buff deadlines`() {
        val f = PotionLifecycleFixture()
        f.level("stat.hitpoints", 1)
        f.level("stat.prayer", 0)
        f.drink("dbrow.effect_toa_silk_dressing")
        f.drink("dbrow.effect_toa_blessed_crystal_scarab")
        f.at(1005)
        f.pulse(ToaOverTimeEffect.SILK_DRESSING_TIMER)
        f.pulse(ToaOverTimeEffect.BLESSED_SCARAB_TIMER)
        f.at(1006)
        f.drink("dbrow.effect_toa_silk_dressing")
        f.drink("dbrow.effect_toa_blessed_crystal_scarab")
        assertEquals(19, f.player.vars[ToaOverTimeEffect.SILK_PULSES])
        assertEquals(9, f.player.vars[ToaOverTimeEffect.SCARAB_PULSES])
        assertEquals(1011, f.timer(ToaOverTimeEffect.SILK_DRESSING_TIMER))
        assertEquals(1010, f.timer(ToaOverTimeEffect.BLESSED_SCARAB_TIMER))
        assertBuff(f, 4373, 1101)
        assertBuff(f, 4374, 1042)
    }

    @Test
    fun `bound death cleanup removes all session state timers native displays and special discount`() {
        val f = PotionLifecycleFixture()
        activateAll(f)
        f.at(1037)
        f.death()
        assertCleared(f)
        for (struct in listOf(4373, 4374, 4375)) assertBuff(f, struct, 0)
        assertEquals(99, f.player.stat("stat.defence"))
        assertEquals(99, f.player.stat("stat.magic"))
        assertEquals(0, messages(f, "have worn off"))
        f.level("stat.hitpoints", 1)
        f.level("stat.prayer", 0)
        TIMERS.forEach(f::pulse)
        assertEquals(1, f.player.stat("stat.hitpoints"))
        assertEquals(0, f.player.stat("stat.prayer"))
        assertCleared(f)
    }

    @Test
    fun `logout and fresh session clear native displays without restoring expired temporary effects`() {
        val f = PotionLifecycleFixture()
        activateAll(f)
        val autosave = f.saved()
        f.logout()
        assertCleared(f)
        f.restore(autosave, 9000)
        assertCleared(f)
        TIMERS.forEach(f::pulse)
        assertCleared(f)
        assertEquals(0, messages(f, "have worn off"))
    }

    @Test
    fun `zero negative and one divisor states cannot leave stale callbacks or a saved discount`() {
        for (value in listOf(0, -1)) {
            val f = PotionLifecycleFixture()
            STATE_VARS.forEach { VarPlayerIntMapSetter.set(f.player, it, value) }
            f.level("stat.hitpoints", 1)
            f.level("stat.prayer", 0)
            TIMERS.forEach(f::pulse)
            assertCleared(f)
            assertEquals(1, f.player.stat("stat.hitpoints"))
            assertEquals(0, f.player.stat("stat.prayer"))
            assertEquals(0, messages(f, "have worn off"))
        }
        val f = PotionLifecycleFixture()
        SpecialAttackEnergyModifier.setCostDivisor(f.player, 1)
        f.death()
        assertEquals(0, f.player.vars["varp.player_special_attack_cost_divisor"])
    }

    private fun activateAll(f: PotionLifecycleFixture) {
        for (effect in listOf(
            "moonlight_potion", "toa_liquid_adrenaline", "toa_smelling_salts",
            "toa_silk_dressing", "toa_blessed_crystal_scarab",
        )) f.drink("dbrow.effect_$effect")
    }

    private fun assertCleared(f: PotionLifecycleFixture) {
        STATE_VARS.forEach { assertEquals(0, f.player.vars[it], it) }
        TIMERS.forEach { assertNull(f.timer(it), it) }
        for (display in listOf(MOONLIGHT_DISPLAY, DIVINE_DEFENCE_DISPLAY, ADRENALINE_DISPLAY, SALTS_DISPLAY)) {
            assertEquals(0, f.player.vars[display], display)
        }
        assertFalse(SpecialAttackEnergyModifier.isActive(f.player))
        assertEquals(50, SpecialAttackEnergyModifier.adjustedCost(f.player, 50))
        assertEquals(0, f.player.vars["varp.player_special_attack_cost_divisor"])
    }

    private fun assertBuff(f: PotionLifecycleFixture, struct: Int, value: Int) {
        val packets = f.client.messages.filterIsInstance<RunClientScript>()
        val expected = RunClientScript(BUFF_SCRIPT.asRSCM(), listOf(struct, value)).toString()
        assertTrue(packets.any { it.toString() == expected }, expected)
        assertTrue(packets.none { it.id in listOf(1089, 1097, 1098) })
    }

    private fun messages(f: PotionLifecycleFixture, text: String): Int =
        f.client.messages.count { text in it.toString() }

    companion object {
        private const val BUFF_SCRIPT = "clientscript.[clientscript,buff_bar_start]"
        private const val MOONLIGHT_DISPLAY = "varbit.moonlight_potion_time"
        private const val DIVINE_DEFENCE_DISPLAY = "varbit.divinedefence_potion_time"
        private const val ADRENALINE_DISPLAY = "varbit.toa_midraidloot_energy_active"
        private const val SALTS_DISPLAY = "varbit.toa_midraidloot_stats_timer"
        private val STATE_VARS = listOf(
            "moonlight_expires_at", "toa_adrenaline_expires_at", "toa_adrenaline_warned",
            "toa_salts_expires_at", "toa_salts_warned", "toa_silk_pulses", "toa_scarab_pulses",
        ).map { "varp.consumables_$it" }
        private val TIMERS = listOf(
            MoonlightPotionEffect.TIMER, ToaLiquidAdrenalineEffect.TIMER,
            ToaSmellingSaltsEffect.TIMER, ToaOverTimeEffect.SILK_DRESSING_TIMER,
            ToaOverTimeEffect.BLESSED_SCARAB_TIMER,
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
