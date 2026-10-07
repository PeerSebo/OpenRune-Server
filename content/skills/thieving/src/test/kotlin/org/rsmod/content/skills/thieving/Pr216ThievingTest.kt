package org.rsmod.content.skills.thieving

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.skills.thieving.configs.Pr216PickpocketTargets
import org.rsmod.content.skills.thieving.configs.ThievingChests
import org.rsmod.content.skills.thieving.configs.ThievingNpcs
import org.rsmod.content.skills.thieving.pets.RockyPet
import org.rsmod.content.skills.thieving.pets.RockyPetManager
import org.rsmod.content.skills.thieving.pets.RockyPetRates
import org.rsmod.content.skills.thieving.pets.RockyPetScript
import org.rsmod.content.skills.thieving.scripts.ThievingGear
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.utils.bits.withBits

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(InternalApi::class)
class Pr216ThievingTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `new target handlers use the actual native Pickpocket slot and never overlap`() {
        val bindings = ThievingNpcs.all.flatMap { target -> target.npcs.map { it to target.pickpocketOp } }
        assertEquals(bindings.size, bindings.map { it.first }.toSet().size)
        val added = Pr216PickpocketTargets.manWoman + Pr216PickpocketTargets.guards + Pr216PickpocketTargets.hamGuards
        assertEquals(93, added.size)
        for (name in added) {
            val op = bindings.single { it.first == name }.second
            val type = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
            assertEquals("Pickpocket", type.actions.getOpOrNull(op - 1), name)
        }
    }

    @Test fun `checking one necklace cannot transfer charges from another`() {
        val bits = checkNotNull(ServerCacheManager.getVarObj("varobj.charges_16383".asRSCM(RSCMType.VAROBJ))).bits
        val fresh = InvObj(ThievingNpcs.DODGY_NECKLACE)
        val used = fresh.copy(vars = fresh.vars.withBits(bits, 3))
        assertEquals(10, ThievingGear.necklaceCharges(fresh))
        assertEquals(3, ThievingGear.necklaceCharges(used))
        assertEquals(10, ThievingGear.necklaceCharges(fresh))
    }

    @Test fun `new stall bindings retain native Steal-from and configured skill requirements`() {
        val stalls = listOf("loc.icthalarins_tea_stall", "loc.contact_tea_stall") +
            listOf("veg", "silk", "fur", "fish", "silver", "spice", "gems", "ore", "cball")
                .map { "loc.port_roberts_market_stall_$it" }
        for (name in stalls) {
            val type = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)))
            assertEquals("Steal-from", type.actions.getOpOrNull(1), name)
            assertTrue(type.isContentType("content.stall"), name)
            assertTrue((type.paramOrNull(params.levelrequire) ?: 0) > 0, name)
        }
    }

    @Test fun `native gear Check is the first extra worn option after Remove`() {
        for (name in listOf(ThievingNpcs.GLOVES_OF_SILENCE, ThievingNpcs.DODGY_NECKLACE)) {
            val type = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ)))
            assertEquals("Check", type.interfaceOptions.getOrNull(2), name)
            assertEquals("Check", type.paramOrNull(params.wear_op1), name)
        }
        val necklace = checkNotNull(ServerCacheManager.getItem(ThievingNpcs.DODGY_NECKLACE.asRSCM(RSCMType.OBJ)))
        assertEquals("Break", necklace.interfaceOptions.getOrNull(3))
    }

    @Test fun `Rocky retains transformation across logout without duplicating its actor`() {
        val f = Fixture()
        val events = EventBus()
        with(RockyPetScript(f.manager)) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        val original = f.manager.spawn(f.player, RockyPet.RACCOON)
        val transformed = f.manager.transform(f.player, RockyPet.TANUKI)
        assertFalse(original.isSlotAssigned)
        assertTrue(f.manager.isOwnedBy(transformed, f.player))
        assertEquals(1, f.npcs.count())
        events.publish(SessionStateEvent.Logout(f.player))
        assertEquals(0, f.npcs.count())
        assertFalse(transformed.isSlotAssigned)
        assertNull(f.manager.following(f.player))
        val savedAttrs = f.player.attr.toPersistentMap()
        val savedVars = f.player.vars.backing.filterKeys { id ->
            ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
        }
        val restored = Player().apply {
            slotId = 2
            uuid = f.player.uuid
            assignUid()
            coords = f.player.coords
            currentMapClock = f.clock.cycle
            attr.putAllFromPersistence(savedAttrs)
            vars.backing.putAll(savedVars)
        }
        assertEquals(RockyPet.TANUKI, f.manager.followingPet(restored))
        assertNull(f.manager.following(restored))
        events.publish(SessionStateEvent.Login(restored))
        val respawned = checkNotNull(f.manager.following(restored))
        assertEquals(RockyPet.TANUKI.npc.asRSCM(RSCMType.NPC), respawned.visType.id)
        assertTrue(f.manager.isOwnedBy(respawned, restored))
        assertEquals(1, f.npcs.count())
        f.manager.tick(restored)
        assertSame(respawned, f.manager.following(restored))
        assertEquals(1, f.npcs.count())
        f.manager.release(restored)
        assertNull(f.manager.followingPet(restored))
        assertNull(f.manager.following(restored))
        assertEquals(0, f.npcs.count())
    }

    @Test fun `Rocky spawn leaves another follower protected and rates match published examples`() {
        val f = Fixture()
        assertFalse(f.manager.hasAnyFollower(f.player))
        VarPlayerIntMapSetter.set(f.player, "varp.follower_npc", (1610 shl 16) or 1)
        assertTrue(f.manager.hasAnyFollower(f.player))
        assertEquals(34_615, RockyPetRates.denominator(RockyPetRates.stall("loc.gemthiefstall"), 75))
        assertEquals(34_015, RockyPetRates.denominator(RockyPetRates.stall("loc.gemthiefstall"), 99))
        assertEquals(96_700, RockyPetRates.denominator(RockyPetRates.pickpocket(ThievingNpcs.hero), 99))
        for (pet in RockyPet.entries) {
            assertEquals(pet, RockyPet.fromObj(pet.objId))
            assertEquals(pet, RockyPet.fromBerries(pet.berries))
            assertNotNull(ServerCacheManager.getNpc(pet.npc.asRSCM(RSCMType.NPC)))
        }
    }

    @Test fun `new rogue chest preserves the existing native classic chest mapping`() {
        val rogue = ThievingChests.all.single { it.loc == "loc.wilderness_rogue_chest" }
        assertEquals(84, rogue.levelReq)
        assertEquals(701.7, rogue.xp)
        assertEquals(34, rogue.respawnTicks)
        assertEquals(47, ThievingChests.all.single { it.loc == "loc.pickchest3" }.levelReq)
        assertEquals(59, ThievingChests.all.single { it.loc == "loc.trapchest4" }.levelReq)
        assertEquals(72, ThievingChests.all.single { it.loc == "loc.trapchest5" }.levelReq)
    }

    private class Fixture {
        val clock = MapClock(100)
        val flags = CollisionFlagMap()
        val npcs = NpcList()
        val manager = RockyPetManager(NpcRepository(clock, NpcRegistry(npcs, flags, EventBus()), npcs), clock, flags)
        val player = Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = CoordGrid(3200, 3200, 0)
            currentMapClock = clock.cycle
        }
        init { for (x in 3192..3208 step 8) for (z in 3192..3208 step 8) flags.allocateIfAbsent(x, z, 0) }
    }
}
