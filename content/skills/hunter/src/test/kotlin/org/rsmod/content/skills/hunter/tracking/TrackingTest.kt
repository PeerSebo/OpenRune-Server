@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter.tracking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
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
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.content.skills.hunter.HunterCatchEvent
import org.rsmod.content.skills.hunter.herbiboarCount
import org.rsmod.content.skills.hunter.pursuitRingUsed
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackingTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun services() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun cleanup() { restore.asReversed().forEach { it() }; restore.clear() }

    @Test fun `all native graph entries resolve and each start reaches a real den`() {
        assertEquals(99, TrackingRoutes.all.sumOf { it.edges.size })
        for (area in TrackingRoutes.all) {
            for (spot in area.starts + area.clues + area.dens) assertNotNull(ServerCacheManager.getObject(spot.id), spot.type)
            for (edge in area.edges) assertNotNull(ServerCacheManager.getVarbit(edge.varbit.asRSCM()), edge.varbit)
            for (start in area.starts) {
                val f = Fixture()
                f.begin(start)
                f.follow()
                val hunt = assertNotNullHunt(f)
                assertTrue(hunt.finished, "${area.prey} ${start.type}")
                assertNotNull(area.byName.getValue(hunt.node).den)
            }
        }
    }

    @Test fun `two players reveal independent native tracks and resetting one leaves the other intact`() {
        val f = Fixture()
        val start = TrackingRoutes.razor.starts.first()
        f.begin(start)
        val first = assertNotNullHunt(f)
        val other = f.player(2).apply { coords = start.coords }
        assertTrue(f.service.start(other, f.bound(start)))
        val otherHunt = checkNotNull(f.service.hunt(other))
        val used = otherHunt.used.first()
        assertTrue(other.vars["varbit.hunting_trail_state$used"] >= 3)
        f.service.clear(f.p)
        assertNull(f.service.hunt(f.p))
        assertSame(otherHunt, f.service.hunt(other))
        assertTrue(other.vars["varbit.hunting_trail_state$used"] >= 3)
        assertNotSame(first, otherHunt)
    }

    @Test fun `wrong or remote clues cannot reveal tracks or give inspection XP`() {
        val f = Fixture()
        f.begin(TrackingRoutes.herbiboar.starts.first())
        val hunt = assertNotNullHunt(f)
        val edge = checkNotNull(hunt.next)
        val proper = checkNotNull(edge.clue(hunt.node))
        val wrong = hunt.area.clues.first { it != proper }
        f.p.coords = wrong.coords
        assertFalse(f.service.inspect(f.p, f.bound(wrong)))
        f.p.coords = proper.coords.translate(4, 0)
        assertFalse(f.service.inspect(f.p, f.bound(proper)))
        assertEquals(1, hunt.used.size)
        assertEquals(0, f.xp("stat.hunter"))
    }

    @Test fun `every kebbit gives its own fur or spike bones meat XP and event once`() {
        for (area in TrackingRoutes.all.filter { it.prey != TrackingPrey.HERBIBOAR }) {
            val f = Fixture()
            f.begin(area.starts.first()); f.follow()
            val hunt = assertNotNullHunt(f)
            val den = checkNotNull(hunt.area.byName.getValue(hunt.node).den)
            f.p.coords = den.coords
            assertTrue(f.service.searchDen(f.p, f.bound(den)))
            f.p.worn[3] = f.item(TrackingService.WAND)
            assertTrue(f.service.catch(f.p, f.bound(den), hunt))
            assertEquals(1, f.p.inv.count(area.prey.loot))
            assertEquals(1, f.p.inv.count("obj.bones"))
            assertEquals(1, f.p.inv.count("obj.spit_raw_beast_meat"))
            assertEquals((area.prey.xp * 10).toInt(), f.xp("stat.hunter"))
            assertEquals(listOf(area.prey.eventKey), f.catches)
            assertFalse(f.service.catch(f.p, f.bound(den), hunt))
        }
    }

    @Test fun `full inventory preserves kebbit and loss of equipped wand invalidates a committed catch`() {
        val f = Fixture()
        val hunt = f.finished(TrackingRoutes.razor)
        val den = checkNotNull(hunt.area.byName.getValue(hunt.node).den)
        f.p.coords = den.coords
        f.service.searchDen(f.p, f.bound(den))
        f.p.worn[3] = f.item(TrackingService.WAND)
        f.p.inv.objs.fill(f.item("obj.logs"))
        assertNull(f.service.canCatch(f.p, f.bound(den)))
        assertSame(hunt, f.service.hunt(f.p))
        f.p.inv.objs.fill(null)
        assertSame(hunt, f.service.canCatch(f.p, f.bound(den)))
        f.p.worn[3] = null
        assertFalse(f.service.catch(f.p, f.bound(den), hunt))
        assertEquals(0, f.xp("stat.hunter")); assertTrue(f.catches.isEmpty())
    }

    @Test fun `pursuit reveals complete kebbit trail consumes character charge and tenth use destroys worn ring`() {
        val f = Fixture()
        f.p.worn[12] = f.item(TrackingService.RING)
        f.p.attr.putAllFromPersistence(mapOf("hunter_pursuit_ring_used" to 9))
        f.begin(TrackingRoutes.razor.starts.first())
        assertTrue(assertNotNullHunt(f).finished)
        assertNull(f.p.worn[12])
        assertEquals(0, f.p.pursuitRingUsed)
        f.service.clear(f.p)
        f.p.attr.putAllFromPersistence(mapOf("hunter_pursuit_ring_used" to 9))
        f.p.worn[12] = f.item(TrackingService.RING)
        f.begin(TrackingRoutes.razor.starts.first())
        assertEquals(1, f.p.pursuitRingUsed)
        assertNotNull(f.p.worn[12])
    }

    @Test fun `equipping pursuit during a trail reveals remainder and does not affect herbiboar`() {
        val f = Fixture()
        f.begin(TrackingRoutes.common.starts.first())
        f.p.worn[12] = f.item(TrackingService.RING)
        f.step()
        assertTrue(assertNotNullHunt(f).finished)
        assertEquals(1, f.p.pursuitRingUsed)
        f.begin(TrackingRoutes.herbiboar.starts.first())
        assertEquals(1, assertNotNullHunt(f).used.size)
        assertEquals(1, f.p.pursuitRingUsed)
    }

    @Test fun `boost only required to start herbiboar and kick XP uses base level without harvesting event`() {
        val f = Fixture()
        f.p.statMap.setBaseLevel("stat.hunter", 77); f.p.statMap.setCurrentLevel("stat.hunter", 80)
        f.begin(TrackingRoutes.herbiboar.starts.first())
        f.p.statMap.setCurrentLevel("stat.hunter", 77)
        f.follow()
        val hunt = assertNotNullHunt(f)
        val den = checkNotNull(hunt.area.byName.getValue(hunt.node).den)
        f.p.coords = den.coords
        val before = f.xp("stat.hunter")
        assertTrue(f.service.catch(f.p, f.bound(den), hunt))
        assertEquals(18600, f.xp("stat.hunter") - before)
        assertTrue(hunt.emerged)
        assertEquals(hunt.area.byName.getValue(hunt.node).denIndex, f.p.vars["varbit.fossil_herbiboar_visible"])
        assertTrue(f.catches.isEmpty())
        assertFalse(f.service.catch(f.p, f.bound(den), hunt))
    }

    @Test fun `full inventory harvest retains rolled herbs and pet without duplicate hunter or herblore XP`() {
        val f = Fixture()
        val hunt = f.emerged()
        val npc = f.herbi(hunt)
        f.random.pet = true
        f.random.herbs = 3
        f.p.inv.objs.fill(f.item("obj.logs"))
        val hunter = f.xp("stat.hunter")
        assertFalse(f.service.harvest(f.p, npc, hunt))
        assertEquals(0, f.xp("stat.herblore")); assertEquals(hunter, f.xp("stat.hunter"))
        val pending = checkNotNull(hunt.harvest)
        assertEquals(2, pending.size)
        f.random.pet = false; f.random.herbs = 1
        for (i in 0..2) f.p.inv[i] = null
        assertFalse(f.service.harvest(f.p, npc, hunt))
        assertEquals(0, f.p.inv.count(pending.first().type))
        f.p.inv[3] = null
        assertTrue(f.service.harvest(f.p, npc, hunt))
        assertEquals(3, f.p.inv.count(pending.first().type))
        assertEquals(1, f.p.inv.count(TrackingService.PET))
        assertEquals(1, f.p.collectionTransmit.count(TrackingService.PET))
        assertEquals(500, f.xp("stat.herblore"))
        assertEquals(listOf(TrackingPrey.HERBIBOAR.eventKey), f.catches)
        assertFalse(f.service.harvest(f.p, npc, hunt))
    }

    @Test fun `magic secateurs in inventory add one herb and level thirty cannot harvest`() {
        val f = Fixture()
        val hunt = f.emerged(); val npc = f.herbi(hunt)
        f.p.statMap.setCurrentLevel("stat.herblore", 30)
        assertNull(f.service.canHarvest(f.p, npc))
        f.p.statMap.setCurrentLevel("stat.herblore", 31)
        f.p.inv[0] = f.item("obj.fairy_enchanted_secateurs")
        assertTrue(f.service.harvest(f.p, npc, hunt))
        assertEquals(250, f.xp("stat.herblore"))
    }

    @Test fun `herbiboar XP follows the special level ninety five transition`() {
        assertEquals(1950.0, HerbiboarLoot.hunterXp(80))
        assertEquals(2370.0, HerbiboarLoot.hunterXp(94))
        assertEquals(2385.0, HerbiboarLoot.hunterXp(95))
        assertEquals(2461.0, HerbiboarLoot.hunterXp(99))
    }

    @Test fun `negative herb bounds exclude high herbs below native minimum levels`() {
        val random = Rolls()
        assertEquals("obj.unidentified_cadantine", HerbiboarLoot.herb(31, random))
        assertEquals("obj.unidentified_ranarr", HerbiboarLoot.herb(33, random))
    }

    @Test fun `pending rare harvest survives logout and a new hunt without rerolling`() {
        val f = Fixture()
        val original = f.emerged()
        f.random.pet = true
        f.p.inv.objs.fill(f.item("obj.logs"))
        assertFalse(f.service.harvest(f.p, f.herbi(original), original))
        f.service.clear(f.p)
        f.random.pet = false
        val next = f.emerged()
        f.p.inv.objs.fill(null)
        assertTrue(f.service.harvest(f.p, f.herbi(next), next))
        assertEquals(1, f.p.inv.count(TrackingService.PET))
        assertEquals(1, f.p.herbiboarCount)
    }

    @Test fun `base Hunter requirement applies only to kebbits and copied foreign scenery cannot start a hunt`() {
        val f = Fixture()
        f.p.statMap.setCurrentLevel("stat.hunter", 48)
        val spot = TrackingRoutes.razor.starts.first()
        f.p.coords = spot.coords
        assertFalse(f.service.start(f.p, f.bound(spot)))
        f.p.statMap.setCurrentLevel("stat.hunter", 99)
        val copied = spot.copy(coords = CoordGrid(3200, 3200))
        f.p.coords = copied.coords
        assertFalse(f.service.start(f.p, f.bound(copied)))
    }

    @Test fun `failed inspection inventory never rerolls fossil or repeats XP`() {
        val f = Fixture()
        f.begin(TrackingRoutes.herbiboar.starts.first())
        f.random.fossil = true
        f.p.inv.objs.fill(f.item("obj.logs"))
        val hunt = assertNotNullHunt(f)
        val clue = checkNotNull(hunt.next?.clue(hunt.node)); f.p.coords = clue.coords
        assertFalse(f.service.inspect(f.p, f.bound(clue)))
        assertEquals(0, f.xp("stat.hunter"))
        f.random.fossil = false
        f.p.inv[0] = null
        assertTrue(f.service.inspect(f.p, f.bound(clue)))
        assertEquals(1, f.p.inv.count("obj.fossil_small_unid"))
        assertEquals(500, f.xp("stat.hunter"))
        assertFalse(f.service.inspect(f.p, f.bound(clue)))
        assertEquals(500, f.xp("stat.hunter"))
    }

    private fun assertNotNullHunt(f: Fixture) = checkNotNull(f.service.hunt(f.p))
    private class Rolls : GameRandom {
        var pet = false
        var fossil = false
        var herbs = 1
        override fun of(maxExclusive: Int): Int = when (maxExclusive) {
            6500 -> if (pet) 0 else 1
            3500 -> if (fossil) 1260 else 3499
            256 -> 0
            else -> 0
        }
        override fun of(minInclusive: Int, maxInclusive: Int) = if (minInclusive == 1 && maxInclusive == 3) herbs else minInclusive
        override fun randomDouble() = 0.0
    }
    private class Fixture {
        val random = Rolls()
        val events = EventBus()
        val catches = mutableListOf<String>()
        val service = TrackingService(random, XpModifiers(emptySet()), events)
        val p = player(1)
        init { events.subscribeUnbound(HunterCatchEvent::class.java) { catches += prey } }
        fun player(slot: Int) = Player().apply {
            uuid = slot.toLong(); slotId = slot; assignUid()
            coords = CoordGrid(2357, 3624)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            invMap["inv.bank"] = Inventory(InventoryServerType(size = 800), arrayOfNulls(800))
            invMap["inv.collection_transmit"] = Inventory(InventoryServerType(size = 2000), arrayOfNulls(2000))
            for (stat in listOf("stat.hunter", "stat.herblore", "stat.hitpoints")) {
                statMap.setBaseLevel(stat, 99); statMap.setCurrentLevel(stat, 99)
            }
        }
        fun item(type: String, count: Int = 1) = InvObj(checkNotNull(ServerCacheManager.getItem(type.asRSCM())), count)
        fun bound(spot: TrackingSpot) = BoundLocInfo(spot.coords, LocEntity(spot.id, 10, 0), 2, 1, 1, 0)
        fun begin(start: TrackingSpot) { p.coords = start.coords; check(service.start(p, bound(start))) }
        fun step() {
            val hunt = checkNotNull(service.hunt(p)); val clue = checkNotNull(hunt.next?.clue(hunt.node))
            p.coords = clue.coords; check(service.inspect(p, bound(clue)))
        }
        fun follow() { repeat(30) { val hunt = service.hunt(p) ?: return; if (!hunt.finished) step() } }
        fun finished(area: TrackingArea): TrackingService.Hunt { begin(area.starts.first()); follow(); return checkNotNull(service.hunt(p)).also { check(it.finished) } }
        fun emerged(): TrackingService.Hunt {
            val hunt = finished(TrackingRoutes.herbiboar); val den = checkNotNull(hunt.area.byName.getValue(hunt.node).den)
            p.coords = den.coords; check(service.catch(p, bound(den), hunt)); return hunt
        }
        fun herbi(hunt: TrackingService.Hunt): Npc {
            val node = hunt.area.byName.getValue(hunt.node)
            val npc = Npc("npc.fossil_herbiboar_${node.denIndex}", checkNotNull(node.den).coords)
            p.coords = npc.coords
            return npc
        }
        fun xp(stat: String) = p.statMap.getFineXP(stat)
    }
    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val prior = field.get(null); restore += { field.set(null, prior) }; field.set(null, value)
    }
}
