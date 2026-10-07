package org.rsmod.content.other.emirsarena.duel

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.player.stat.PlayerStatResetHook
import org.rsmod.api.player.stat.PlayerStatResetService
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.other.emirsarena.EmirsArena
import org.rsmod.content.other.emirsarena.EmirsArenaAttributes
import org.rsmod.content.other.emirsarena.EmirsArenaState
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class DuelStatResetTest {
    @Test
    fun `forfeit fully resets both players once through the activity reset service`() {
        val f = Fixture()

        f.manager.forfeit(f.duel, f.loser)
        f.manager.forfeit(f.duel, f.loser)

        assertEquals(listOf(f.winner, f.loser), f.resets)
        assertEquals(listOf(70, 70), f.attackBeforeReset)
        assertEquals(50, f.winner.stat("stat.attack"))
        assertEquals(50, f.loser.stat("stat.attack"))
        assertEquals(10_000, f.winner.runEnergy)
        assertEquals(10_000, f.loser.runEnergy)
        assertEquals(DuelStage.Finished, f.duel.stage)
        assertNull(f.manager.duelOf(f.winner))
        assertNull(f.manager.duelOf(f.loser))
    }

    @Test
    fun `death finish resets the winner and leaves the loser to ordinary death cleanup`() {
        val f = Fixture()

        f.manager.finishAfterDeath(f.loser)
        f.manager.finishAfterDeath(f.loser)

        assertEquals(listOf(f.winner), f.resets)
        assertEquals(50, f.winner.stat("stat.attack"))
        assertEquals(70, f.loser.stat("stat.attack"))
        assertEquals(DuelStage.Finished, f.duel.stage)
    }

    @Test
    fun `logout finish keeps the existing losing player save behavior and resets only the winner`() {
        val f = Fixture()
        f.loser.loggingOut = true

        f.manager.onLogout(f.loser)
        f.manager.onLogout(f.loser)

        assertEquals(listOf(f.winner), f.resets)
        assertEquals(50, f.winner.stat("stat.attack"))
        assertEquals(70, f.loser.stat("stat.attack"))
        assertEquals(100, f.loser.runEnergy)
        assertEquals(DuelStage.Finished, f.duel.stage)
    }

    @Test
    fun `legacy duel completion imports old records without changing ranked streaks or rewards`() {
        val f = Fixture()
        f.winner.attr.putAllFromPersistence(mapOf(
            "emirs_arena_legacy_wins" to 12,
            "emirs_arena_ranked_wins" to 7,
            "emirs_arena_win_streak" to 8,
        ))
        f.loser.attr.putAllFromPersistence(mapOf(
            "emirs_arena_legacy_losses" to 4,
            "emirs_arena_ranked_losses" to 2,
            "emirs_arena_win_streak" to 3,
        ))

        repeat(2) { f.manager.forfeit(f.duel, f.loser) }

        assertEquals(13, EmirsArenaState.wins(f.winner, ranked = false))
        assertEquals(5, EmirsArenaState.losses(f.loser, ranked = false))
        assertEquals(7, EmirsArenaState.wins(f.winner, ranked = true))
        assertEquals(2, EmirsArenaState.losses(f.loser, ranked = true))
        assertEquals(8, EmirsArenaState.winStreak(f.winner))
        assertEquals(3, EmirsArenaState.winStreak(f.loser))
        assertEquals(0, f.winner.vars[EmirsArena.REWARD_POINTS_VARBIT])
        assertEquals(0, f.loser.vars[EmirsArena.REWARD_POINTS_VARBIT])
        assertTrue(f.winner.attr.toPersistentMap().isEmpty())
        assertTrue(f.loser.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `ranked duel uses the imported streak for reward then increments winner and clears loser`() {
        val f = Fixture(ranked = true)
        f.winner.attr.putAllFromPersistence(mapOf(
            "emirs_arena_ranked_wins" to 7,
            "emirs_arena_win_streak" to 3,
        ))
        f.loser.attr.putAllFromPersistence(mapOf(
            "emirs_arena_ranked_losses" to 2,
            "emirs_arena_win_streak" to 8,
        ))

        repeat(2) { f.manager.forfeit(f.duel, f.loser) }

        assertEquals(8, EmirsArenaState.wins(f.winner, ranked = true))
        assertEquals(3, EmirsArenaState.losses(f.loser, ranked = true))
        assertEquals(4, EmirsArenaState.winStreak(f.winner))
        assertEquals(0, EmirsArenaState.winStreak(f.loser))
        assertEquals(19, f.winner.vars[EmirsArena.REWARD_POINTS_VARBIT])
        assertEquals(12, f.loser.vars[EmirsArena.REWARD_POINTS_VARBIT])
        assertEquals(2525, f.winner.vars[EmirsArena.RANK_POINTS_VARBIT])
        assertEquals(2475, f.loser.vars[EmirsArena.RANK_POINTS_VARBIT])
        assertEquals(0, EmirsArenaState.wins(f.winner, ranked = false))
        assertEquals(0, EmirsArenaState.losses(f.loser, ranked = false))
        f.loser.attr.putAllFromPersistence(mapOf("emirs_arena_win_streak" to 8))
        f.manager.onLogin(f.loser)
        assertEquals(0, EmirsArenaState.winStreak(f.loser))
        assertTrue(f.loser.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `login migrates records even when the player has no duel`() {
        val f = Fixture()
        f.winner.attr.remove(EmirsArenaAttributes.DUEL)
        f.winner.attr.putAllFromPersistence(mapOf("emirs_arena_ranked_wins" to 12))

        f.manager.onLogin(f.winner)

        assertEquals(12, f.winner.vars["varp.emirs_arena_ranked_wins"])
        assertEquals(1, f.winner.vars["varp.emirs_arena_state_migrated"])
        assertTrue(f.winner.attr.toPersistentMap().isEmpty())
    }

    private class Fixture(ranked: Boolean = false) {
        val resets = mutableListOf<Player>()
        val attackBeforeReset = mutableListOf<Int>()
        private val eventBus = EventBus()
        private val attackManager = unconstructed(PlayerAttackManager::class.java).also {
            PlayerAttackManager::class.java.getDeclaredField("eventBus").apply {
                isAccessible = true
                set(it, eventBus)
            }
        }
        private val resetService = PlayerStatResetService(setOf(PlayerStatResetHook {
            resets += it
            attackBeforeReset += it.stat("stat.attack")
        }))
        val manager = DuelManager(
            eventBus,
            attackManager,
            WornUnequipOp(eventBus),
            unconstructed(LocRepository::class.java),
            AreaChecker(unconstructed(RegionRegistry::class.java), AreaIndex()),
            DuelStakes(eventBus, ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap()))),
            resetService,
        )
        val winner = player(1)
        val loser = player(2)
        val duel = Duel(winner, loser, ranked = ranked).also {
            it.stage = DuelStage.Fighting
            winner.attr[EmirsArenaAttributes.DUEL] = it
            loser.attr[EmirsArenaAttributes.DUEL] = it
        }

        private fun player(id: Int) = Player().apply {
            slotId = id
            uuid = id.toLong()
            observerUUID = id.toLong()
            assignUid()
            displayName = "Player $id"
            coords = EmirsArena.LOBBY
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            runEnergy = 100
            for (type in ServerCacheManager.getStats().values) {
                val stat = RSCM.getReverseMapping(RSCMType.STAT, type.id)
                statMap.setBaseLevel(stat, 50)
                statMap.setCurrentLevel(stat, 70)
            }
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

        private fun <T> unconstructed(type: Class<T>): T {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
            return type.cast(unsafe.allocateInstance(type))
        }
    }
}
