package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.specials.SpecialAttack
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.specials.weapon.SpecialAttackWeapons
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.inv.InvObj
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class TheatreDawnbringerTest {
    @Test fun `native dawnbringer requirement is thirty five percent`() {
        assertEquals(350, special().energyInHundreds)
    }

    @Test fun `pulsate rejects outside raid without spending energy`() = runBlocking {
        val fixture = TheatreLifecycleTest.Fixture()
        val player = fixture.player()
        val target = Npc("npc.verzik_phase1", player.coords)
        assertRejected(fixture, player, target)
    }

    @Test fun `pulsate rejects player targets without spending energy`() = runBlocking {
        val fixture = TheatreLifecycleTest.Fixture()
        assertRejected(fixture, fixture.player(), fixture.player())
    }

    @Test fun `raid attack gate excludes spectators other rooms and later phases`() {
        assertTrue(TheatreDawnbringerRules.allowed(true, 5, 0, true, true))
        assertFalse(TheatreDawnbringerRules.allowed(false, 5, 0, true, true))
        assertFalse(TheatreDawnbringerRules.allowed(true, 4, 0, true, true))
        assertFalse(TheatreDawnbringerRules.allowed(true, 5, 1, true, true))
        assertFalse(TheatreDawnbringerRules.allowed(true, 5, 2, true, true))
        assertFalse(TheatreDawnbringerRules.allowed(true, 5, 0, false, true))
        assertFalse(TheatreDawnbringerRules.allowed(true, 5, 0, true, false))
    }

    @Test fun `powered attack matches documented magic level thresholds`() {
        assertEquals(2, TheatreDawnbringerRules.normalMaxHit(1))
        assertEquals(9, TheatreDawnbringerRules.normalMaxHit(60))
        assertEquals(14, TheatreDawnbringerRules.normalMaxHit(92))
        assertEquals(15, TheatreDawnbringerRules.normalMaxHit(99))
        assertEquals(18, TheatreDawnbringerRules.normalMaxHit(115))
    }

    private suspend fun assertRejected(
        fixture: TheatreLifecycleTest.Fixture,
        player: org.rsmod.game.entity.Player,
        target: PathingEntity,
    ) {
        VarPlayerIntMapSetter.set(player, "varp.sa_energy", 1000)
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
        val attack = CombatAttack.Staff(InvObj("obj.verzik_special_weapon"), null)
        val special = special(fixture)
        assertFalse(special.attack(access, target, attack))
        assertEquals(1000, player.vars["varp.sa_energy"])
    }

    private fun special(
        fixture: TheatreLifecycleTest.Fixture = TheatreLifecycleTest.Fixture(),
    ): SpecialAttack.Magic {
        val native = SpecialAttackWeapons()
        SpecialAttackWeapons::class.java.declaredMethods.single { it.name.startsWith("startup") }
            .apply { isAccessible = true }.invoke(native)
        val attackManager = unsafe.allocateInstance(PlayerAttackManager::class.java) as PlayerAttackManager
        PlayerAttackManager::class.java.getDeclaredField("eventBus")
            .apply { isAccessible = true }.set(attackManager, EventBus())
        val registry = SpecialAttackRegistry(native)
        val repository = SpecialAttackRepository(registry)
        val dawnbringer = TheatreDawnbringer(fixture.manager, DefaultGameRandom(1),
            WeaponAttackManager(attackManager))
        with(dawnbringer) {
            repository.register(SpecialAttackManager(SpecialAttackEnergy(), native, attackManager))
        }
        return registry[InvObj("obj.verzik_special_weapon")] as SpecialAttack.Magic
    }

    companion object {
        private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }.get(null) as Unsafe

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
