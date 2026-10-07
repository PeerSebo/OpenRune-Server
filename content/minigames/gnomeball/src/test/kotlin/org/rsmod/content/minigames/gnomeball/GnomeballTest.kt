@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.minigames.gnomeball

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GnomeballTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun transactions() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun cleanup() { restore.asReversed().forEach { it() }; restore.clear() }

    @Test fun `vyre bankers face north and remain stationary`() {
        for (symbol in listOf("npc.vampyre_banker_female", "npc.vampyre_banker_male")) {
            val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM()))
            assertEquals(Direction.North, type.respawnDir)
            assertEquals(0, type.wanderRange)
        }
    }

    @Test fun `entry removes both hands without losing item variables`() {
        val f = Fixture()
        f.player.worn[3] = InvObj("obj.bronze_sword", vars = 234)
        f.player.worn[5] = InvObj("obj.wooden_shield", vars = 567)
        assertTrue(f.game.enter(f.player))
        assertNull(f.player.worn[3]); assertNull(f.player.worn[5])
        assertEquals(234, f.player.inv.objs.first { it?.id == "obj.bronze_sword".asRSCM() }?.vars)
        assertEquals(567, f.player.inv.objs.first { it?.id == "obj.wooden_shield".asRSCM() }?.vars)
        assertEquals(1, f.game.sessionCount())
    }

    @Test fun `full inventory prevents entry without changing equipment`() {
        val f = Fixture()
        f.player.inv.objs.fill(InvObj("obj.logs"))
        f.player.worn[3] = InvObj("obj.bronze_sword")
        assertFalse(f.game.enter(f.player))
        assertFalse(GnomeballState.active(f.player))
        assertEquals(1, f.player.worn.count("obj.bronze_sword"))
    }

    @Test fun `coordinates alone cannot authorise a shot or a reward`() {
        val f = Fixture()
        f.player.worn[3] = InvObj(GnomeballState.Ball)
        assertFalse(f.game.shoot(f.player))
        assertFalse(f.game.holding(f.player))
        assertEquals(0, f.xp("stat.ranged"))
    }

    @Test fun `referee cannot give a second ball during a throw or while holding it`() {
        val f = Fixture()
        f.game.enter(f.player)
        assertTrue(f.game.requestBall(f.player, f.referee))
        assertFalse(f.game.requestBall(f.player, f.referee))
        f.arrive()
        assertTrue(f.game.holding(f.player))
        assertFalse(f.game.requestBall(f.player, f.referee))
        assertEquals(1, f.player.worn.count(GnomeballState.Ball))
    }

    @Test fun `five goals give native score XP and one exit reward only`() {
        val f = Fixture()
        f.win()
        assertEquals(0, f.player.vars[GnomeballState.Score])
        assertEquals(GnomeballState.Won, f.player.vars[GnomeballState.Phase])
        assertEquals(520, f.xp("stat.agility")); assertEquals(520, f.xp("stat.ranged"))
        assertFalse(f.game.requestBall(f.player, f.referee))
        f.game.leave(f.player)
        assertEquals(1, f.player.inv.count(GnomeballState.Ball))
        f.game.leave(f.player)
        assertEquals(1, f.player.inv.count(GnomeballState.Ball))
        assertEquals(0, f.game.sessionCount())
    }

    @Test fun `a missed goal gives neither score nor XP and permits a new ball`() {
        val f = Fixture()
        f.takeBall()
        f.random.roll = 99
        assertTrue(f.game.shoot(f.player))
        f.arrive()
        assertEquals(0, f.player.vars[GnomeballState.Score])
        assertEquals(0, f.xp("stat.agility"))
        f.advance(4)
        assertTrue(f.game.requestBall(f.player, f.referee))
    }

    @Test fun `shot limits improve with level and worsen with distance`() {
        assertTrue(GnomeballData.shotChance(99, 1) > GnomeballData.shotChance(1, 1))
        assertTrue(GnomeballData.shotChance(99, 1) > GnomeballData.shotChance(99, 10))
        val f = Fixture()
        f.takeBall()
        f.player.coords = CoordGrid(2384, 3488)
        assertFalse(f.game.shoot(f.player))
        assertTrue(f.game.holding(f.player))
    }

    @Test fun `brought balls are returned and do not manufacture a win reward`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(GnomeballState.Ball, vars = 123)
        f.player.inv[1] = InvObj(GnomeballState.Ball, vars = 456)
        f.win()
        f.game.leave(f.player)
        assertEquals(2, f.player.inv.count(GnomeballState.Ball))
        assertEquals(setOf(123, 456), f.player.inv.objs.filterNotNull().map { it.vars }.toSet())
    }

    @Test fun `full inventory retains earned and owned balls in protected permanent storage`() {
        val f = Fixture()
        f.win()
        f.player.inv.objs.fill(InvObj("obj.logs"))
        f.game.leave(f.player)
        val deposit = checkNotNull(f.player.invMap[GnomeballState.Deposit])
        assertEquals(1, deposit.count(GnomeballState.Ball))
        f.player.inv[0] = null
        f.game.claim(f.player); f.game.claim(f.player)
        assertEquals(1, f.player.inv.count(GnomeballState.Ball))
        assertTrue(deposit.isEmpty())
    }

    @Test fun `loan markers distinguish personal balls and are removed from bank transfers`() {
        val f = Fixture()
        f.takeBall()
        val loan = checkNotNull(f.player.worn[3])
        val bank = f.player.invMap.getOrPut("inv.bank")
        bank[0] = InvObj(GnomeballState.Ball)
        bank[1] = loan
        f.player.worn[3] = null
        f.game.equipmentChanged(f.player)
        assertNull(bank[1]); assertNotNull(bank[0])
        assertEquals(GnomeballState.Empty, f.player.vars[GnomeballState.Phase])
    }

    @Test fun `logout cancels a goal in flight and stale callbacks cannot grant rewards`() {
        val f = Fixture()
        f.takeBall()
        f.game.shoot(f.player)
        val old = checkNotNull(f.game.flight(f.player))
        f.game.leave(f.player)
        f.game.enter(f.player)
        f.game.finishFlight(f.player, old)
        assertEquals(0, f.player.vars[GnomeballState.Score])
        assertEquals(0, f.player.inv.count(GnomeballState.Ball))
        assertNull(f.game.flight(f.player))
    }

    @Test fun `normal unequip and bank deposit synchronously dispose of the loan without touching owned balls`() {
        for (banking in listOf(false, true)) {
            val f = Fixture()
            f.takeBall()
            val target = if (banking) f.player.invMap.getOrPut("inv.bank") else f.player.inv
            target[0] = InvObj(GnomeballState.Ball, 3)
            val result = WornUnequipOp(f.events).unequip(f.player, 3, f.player.worn, target)
            assertEquals(WornUnequipResult.Success, result)
            assertNull(f.player.worn[3])
            assertEquals(3, target.count(GnomeballState.Ball))
            assertTrue(target.objs.none { GnomeballState.loan(it) })
        }
    }

    @Test fun `login recovery strips a saved loan marker even after temporary variables reset`() {
        val f = Fixture()
        f.takeBall()
        GnomeballState.clear(f.player)
        val bank = f.player.invMap.getOrPut("inv.bank")
        bank[0] = InvObj(GnomeballState.Ball)
        f.game.leave(f.player)
        assertNull(f.player.worn[3])
        assertEquals(1, bank.count(GnomeballState.Ball))
    }

    @Test fun `walking out during a referee throw leaves no loan or session`() {
        val f = Fixture()
        f.game.enter(f.player)
        f.game.requestBall(f.player, f.referee)
        f.player.coords = CoordGrid(2382, 3488)
        f.advance(2); f.game.tick()
        assertFalse(GnomeballState.active(f.player))
        assertNull(f.player.worn[3])
        assertEquals(0, f.game.sessionCount())
    }

    @Test fun `winger catches and returns a reserved ball without XP`() {
        val f = Fixture()
        f.takeBall()
        val winger = f.npc("npc.gnomeballer_green", f.player.coords.translateX(-1))
        assertTrue(f.game.pass(f.player, winger))
        f.arrive()
        assertEquals("npc.gnomeballer_green_withball".asRSCM(), winger.visType.id)
        assertFalse(f.game.holding(f.player))
        f.advance(GnomeballData.wingerTicks); f.game.tick()
        f.arrive()
        assertTrue(f.game.holding(f.player))
        assertEquals(0, f.game.holderCount())
        assertEquals(0, f.xp("stat.ranged"))
    }

    @Test fun `one winger cannot accept overlapping throws from two players`() {
        val f = Fixture()
        f.takeBall()
        val other = f.player(2)
        f.game.enter(other); f.game.requestBall(other, f.referee)
        f.advance(2); f.game.tick(); f.advance(1)
        val winger = f.npc("npc.gnomeballer_green", f.player.coords.translateX(-1))
        assertTrue(f.game.pass(f.player, winger))
        assertFalse(f.game.pass(other, winger))
        assertTrue(f.game.holding(other))
    }

    @Test fun `deleting a winger cancels its custody and permits another referee throw`() {
        val f = Fixture()
        f.takeBall()
        val winger = f.npc("npc.gnomeballer_green", f.player.coords.translateX(-1))
        f.game.pass(f.player, winger); f.arrive()
        f.npcs.del(winger, Int.MAX_VALUE)
        f.advance(5); f.game.tick()
        assertEquals(0, f.game.holderCount())
        assertTrue(f.game.requestBall(f.player, f.referee))
    }

    @Test fun `other players can receive a pass only while participating and empty handed`() {
        val f = Fixture()
        f.takeBall()
        val other = f.player(2)
        assertFalse(f.game.pass(f.player, other))
        f.game.enter(other)
        assertTrue(f.game.pass(f.player, other))
        assertFalse(f.game.requestBall(other, f.referee))
        f.arrive()
        assertTrue(f.game.holding(other))
        assertFalse(f.game.holding(f.player))
        assertEquals(0, other.statMap.getFineXP("stat.ranged"))
    }

    @Test fun `sender logout releases a recipient reservation`() {
        val f = Fixture()
        f.takeBall()
        val other = f.player(2)
        f.game.enter(other); f.game.pass(f.player, other)
        f.game.leave(f.player)
        assertEquals(GnomeballState.Empty, other.vars[GnomeballState.Phase])
        assertTrue(f.game.requestBall(other, f.referee))
    }

    @Test fun `a stolen ball can be tackled back before it returns to the referee`() {
        val f = Fixture()
        f.takeBall()
        val defender = f.npc("npc.gnomeballer_red", f.player.coords.translateX(1))
        f.game.tick()
        assertEquals(GnomeballState.Stolen, f.player.vars[GnomeballState.Phase])
        assertEquals(1, f.game.holderCount())
        f.advance(GnomeballData.tackleTicks)
        assertTrue(f.game.tackle(f.player, defender))
        assertTrue(f.game.holding(f.player))
        assertEquals(0, f.game.holderCount())
    }

    @Test fun `equipping a weapon is refused during a game and allowed after leaving`() {
        val f = Fixture()
        val hook = GnomeballRestrictions()
        val type = checkNotNull(ServerCacheManager.getItem("obj.bronze_sword".asRSCM()))
        val action = org.rsmod.api.player.hook.RestrictedAction.Equip(type)
        f.game.enter(f.player)
        assertNotNull(hook.restriction(f.player, action))
        f.game.leave(f.player)
        assertNull(hook.restriction(f.player, action))
    }

    @Test fun `session variables are temporary and ball deposits persist and stay protected`() {
        for (symbol in listOf(GnomeballState.Session, GnomeballState.Phase, GnomeballState.Cooldown, GnomeballState.Brought)) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Temp, type.scope)
        }
        val deposit = Inventory.create(GnomeballState.Deposit)
        assertTrue(deposit.type.protect)
        assertEquals(30, deposit.size)
        assertNotNull(ServerCacheManager.getInterface("interface.gnomeball".asRSCM()))
    }

    private class TestRandom : GameRandom {
        var roll = 0
        override fun of(maxExclusive: Int) = roll.coerceAtMost(maxExclusive - 1)
        override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive + roll.coerceAtMost(maxInclusive - minInclusive)
        override fun randomDouble() = roll / 100.0
    }
    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val random = TestRandom()
        val collision = CollisionFlagMap().apply { for (x in 2376..2416 step 8) for (z in 3472..3504 step 8) allocateIfAbsent(x, z, 0) }
        val npcList = NpcList()
        val registry = NpcRegistry(npcList, collision, events)
        val npcs = NpcRepository(clock, registry, npcList)
        val game = GnomeballGame(clock, npcList, WorldRepository(ZoneUpdateMap()), RayCastValidator(collision), random, XpModifiers(emptySet()), StandardPlayerHitModifier(events), events)
        val player = player(1)
        val referee = npc("npc.gnomereferee", CoordGrid(2396, 3489))
        init {
            events.subscribeUnbound(NpcStateEvents.Create::class.java) { game.spawned(npc) }
            events.subscribeUnbound(NpcStateEvents.Respawn::class.java) { game.spawned(npc) }
            events.subscribeUnbound(NpcStateEvents.Delete::class.java) { game.deleted(npc) }
            events.subscribeUnbound(HeldEquipEvents.WearposChange::class.java) { game.equipmentChanged(player) }
        }
        fun player(slot: Int) = Player().apply {
            uuid = slot.toLong(); observerUUID = uuid; slotId = slot; assignUid()
            coords = CoordGrid(2397, 3489)
            currentMapClock = clock.cycle; processedMapClock = clock.cycle
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            for (stat in listOf("stat.hitpoints", "stat.agility", "stat.ranged")) {
                statMap.setBaseLevel(stat, 99); statMap.setCurrentLevel(stat, 99)
            }
        }
        fun npc(symbol: String, coords: CoordGrid) = Npc(symbol, coords).apply { hitpoints = 10; npcs.add(this, Int.MAX_VALUE) }
        fun advance(ticks: Int) { clock.cycle += ticks; player.currentMapClock = clock.cycle; player.processedMapClock = clock.cycle }
        fun arrive() { val flight = checkNotNull(game.flight(player)); advance(flight.arrives - clock.cycle); game.tick() }
        fun takeBall() { check(game.enter(player)); check(game.requestBall(player, referee)); arrive(); advance(1) }
        fun win() {
            check(game.enter(player))
            repeat(5) { check(game.requestBall(player, referee)); arrive(); advance(1); check(game.shoot(player)); arrive(); advance(4) }
        }
        fun xp(stat: String) = player.statMap.getFineXP(stat)
    }
    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null); restore += { field.set(null, previous) }; field.set(null, value)
    }
}
