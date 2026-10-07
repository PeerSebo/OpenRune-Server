package org.rsmod.content.other.pets.cats

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcMode
import dev.openrune.types.varp.VarpLifetime
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(InternalApi::class)
class CatPetLifecycleTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `cat catalogue maps every stage and colour to unique native actors`() {
        assertEquals(28, CatPet.all.size)
        assertEquals(28, CatPet.all.map { it.objId }.toSet().size)
        assertEquals(28, CatPet.all.map { it.npcId }.toSet().size)
        CatPet.all.forEach { assertEquals(it, CatPet.fromObj(it.objId)) }
        assertEquals(CatPet(CatStage.CAT, CatColour.HELL), CatPet(CatStage.KITTEN, CatColour.HELL).grown())
        assertNull(CatPet(CatStage.LAZY, CatColour.BLACK).grown())
    }

    @Test fun `growth replaces the actor and preserves owner and colour`() {
        val f = Fixture()
        val kitten = f.manager.spawn(f.player, CatPet(CatStage.KITTEN, CatColour.BROWN))
        assertTrue(f.manager.forceGrowth(f.player))
        f.manager.tick(f.player)
        val grown = checkNotNull(f.manager.following(f.player))
        assertFalse(kitten.isSlotAssigned)
        assertEquals(1, f.npcs.count())
        assertTrue(f.manager.isOwnedBy(grown, f.player))
        assertEquals(CatPet(CatStage.CAT, CatColour.BROWN), f.manager.followingPet(f.player))
        assertEquals(0, f.player.catGrowth)
        assertEquals(0, f.player.catHunger)
        assertEquals(0, f.player.catAttention)
    }

    @Test fun `logout retains following cat and growth but permanent release removes them`() {
        val f = Fixture()
        f.manager.spawn(f.player, CatPet(CatStage.KITTEN, CatColour.LIGHT))
        f.manager.tick(f.player)
        f.manager.setChasing(f.player, true)
        f.logout()
        assertEquals(0, f.npcs.count())
        assertEquals(1, f.player.catGrowth)
        assertEquals(1, f.player.catHunger)
        assertEquals(1, f.player.catAttention)
        assertFalse(f.player.catChasing)
        assertFalse(f.player.catResyncFollower)
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
        f.reload()
        f.login()
        assertEquals(CatPet(CatStage.KITTEN, CatColour.LIGHT), f.manager.followingPet(f.player))
        assertEquals(1, f.npcs.count())
        assertEquals(1, f.player.catGrowth)
        assertFalse(f.player.catChasing)
        assertTrue(f.player.catResyncFollower)
        f.manager.release(f.player)
        assertNull(f.manager.followingPet(f.player))
        assertEquals(0, f.player.catGrowth)
        assertEquals(0, f.player.catHunger)
        assertEquals(0, f.player.catAttention)
        f.reload()
        f.login()
        assertFalse(f.manager.hasFollower(f.player))
    }

    @Test fun `hunger runaway stops the tick before writing orphan attention state`() {
        val f = Fixture()
        f.manager.spawn(f.player, CatPet(CatStage.KITTEN, CatColour.DEFAULT))
        f.player.catHunger = CatPetManager.HUNGER_RUN - 1
        f.manager.tick(f.player)
        assertNull(f.manager.followingPet(f.player))
        val attrs = f.player.attr.toPersistentMap()
        assertFalse(attrs.containsKey("pets.cat.hunger"))
        assertFalse(attrs.containsKey("pets.cat.attention"))
        assertEquals(0, f.player.catHunger)
        assertEquals(0, f.player.catAttention)
        assertFalse(CatPetManager.TICK_TIMER in f.player.softTimerMap)
    }

    @Test fun `old following save restores the actor and growth without counting offline cycles`() {
        val f = Fixture()
        val pet = CatPet(CatStage.KITTEN, CatColour.BLUEGREY)
        f.player.attr.putAllFromPersistence(mapOf(
            "pets.cat.following_obj" to pet.objId,
            "pets.cat.growth" to 400,
            "pets.cat.hunger" to 200,
            "pets.cat.attention" to -2400,
            "quest.progress" to 5,
        ))
        f.clock.cycle = 9000
        f.player.currentMapClock = 9000

        f.login()

        val npc = checkNotNull(f.manager.following(f.player))
        assertTrue(f.manager.isOwnedBy(npc, f.player))
        assertEquals(pet, f.manager.followingPet(f.player))
        assertEquals(400, f.player.catGrowth)
        assertEquals(200, f.player.catHunger)
        assertEquals(-2400, f.player.catAttention)
        assertEquals(mapOf("quest.progress" to 5), f.player.attr.toPersistentMap())
        assertTrue(CatPetManager.TICK_TIMER in f.player.softTimerMap)
    }

    @Test fun `login restores follower options on the first processed tick only`() {
        val f = Fixture()
        f.player.catFollowingObj = CatPet(CatStage.CAT, CatColour.DEFAULT).objId
        f.login()
        val npc = checkNotNull(f.manager.following(f.player))
        val packed = (npc.visType.id shl 16) or (npc.slotId and 0xffff)
        val packet = VarpLarge("varp.follower_npc".asRSCM(), packed)
        assertFalse(packet in f.client.messages)

        f.player.processedMapClock = f.clock.cycle
        f.manager.tick(f.player)
        f.manager.tick(f.player)

        assertEquals(1, f.client.messages.count { it == packet })
        assertEquals(packed, f.player.vars["varp.follower_npc"])
        assertFalse(f.player.catResyncFollower)
    }

    @Test fun `chasing suspends following until stopped and is never saved`() {
        val f = Fixture()
        val npc = f.manager.spawn(f.player, CatPet(CatStage.CAT, CatColour.DEFAULT))
        f.manager.setChasing(f.player, true)
        npc.mode = NpcMode.None
        npc.resetFaceEntity()

        f.manager.tick(f.player)

        assertEquals(NpcMode.None, npc.mode)
        assertFalse(npc.isFacingPlayer)
        assertFalse("varp.pets_cat_chasing".asRSCM() in f.savedVars())
        f.manager.setChasing(f.player, false)
        f.manager.tick(f.player)
        assertEquals(NpcMode.None, npc.mode)
        assertTrue(npc.isFacingPlayer)
    }

    @Test fun `lost actor respawns without resetting needs or growth`() {
        val f = Fixture()
        val pet = CatPet(CatStage.KITTEN, CatColour.BROWN)
        val first = f.manager.spawn(f.player, pet)
        f.manager.tick(f.player)
        f.repo.del(first, Int.MAX_VALUE)

        f.manager.tick(f.player)

        val restored = checkNotNull(f.manager.following(f.player))
        assertNotSame(first, restored)
        assertTrue(f.manager.isOwnedBy(restored, f.player))
        assertEquals(pet, f.manager.followingPet(f.player))
        assertEquals(1, f.player.catGrowth)
        assertEquals(1, f.player.catHunger)
        assertEquals(1, f.player.catAttention)
    }

    @Test fun `feeding stroking and wool preserve their exact needs thresholds across saves`() {
        val f = Fixture()
        f.manager.spawn(f.player, CatPet(CatStage.KITTEN, CatColour.DEFAULT))
        f.player.catHunger = CatPetManager.HUNGER_WARN
        f.player.catAttention = CatPetManager.ATTENTION_WARN
        assertTrue(f.manager.isHungry(f.player))
        assertTrue(f.manager.isLonely(f.player))

        f.manager.feed(f.player)
        f.manager.stroke(f.player)

        assertFalse(f.manager.isHungry(f.player))
        assertFalse(f.manager.isLonely(f.player))
        assertEquals(700, f.player.catAttention)
        f.manager.stroke(f.player)
        assertEquals(0, f.player.catAttention)
        f.manager.play(f.player)
        assertEquals(-2600, f.player.catAttention)
        f.logout()
        f.reload()
        f.login()
        f.manager.tick(f.player)
        assertEquals(-2599, f.player.catAttention)
        assertEquals(1, f.player.catHunger)
    }

    @Test fun `attention runaway clears follower state and schedules actor disappearance`() {
        val f = Fixture()
        val npc = f.manager.spawn(f.player, CatPet(CatStage.KITTEN, CatColour.DEFAULT))
        f.player.catAttention = CatPetManager.ATTENTION_RUN - 1
        f.manager.setChasing(f.player, true)

        f.manager.tick(f.player)

        assertNull(f.manager.following(f.player))
        assertNull(f.manager.followingPet(f.player))
        assertEquals(-1, f.player.vars["varp.follower_npc"])
        assertEquals(0, f.player.catGrowth)
        assertEquals(0, f.player.catHunger)
        assertEquals(0, f.player.catAttention)
        assertFalse(f.player.catChasing)
        assertFalse(f.player.catResyncFollower)
        assertEquals(NpcMode.None, npc.mode)
        assertEquals(f.clock.cycle + 6, npc.lifecycleDelCycle)
        assertFalse(CatPetManager.TICK_TIMER in f.player.softTimerMap)
    }

    @Test fun `pickup and death permanently clear the old cat before a new follower is placed`() {
        for (die in listOf(false, true)) {
            val f = Fixture()
            val npc = f.manager.spawn(f.player, CatPet(CatStage.KITTEN, CatColour.DEFAULT))
            f.manager.tick(f.player)
            f.manager.setChasing(f.player, true)

            if (die) CatPetDeathHook(f.manager).cleanup(f.player) else f.manager.release(f.player)

            assertFalse(npc.isSlotAssigned)
            assertFalse(f.manager.hasFollower(f.player))
            assertNull(f.manager.followingPet(f.player))
            assertFalse(f.player.catChasing)
            assertFalse(f.player.catResyncFollower)
            f.player.attr.putAllFromPersistence(mapOf("pets.cat.growth" to 17000))
            f.manager.spawn(f.player, CatPet(CatStage.KITTEN, CatColour.LIGHT))
            assertEquals(0, f.player.catGrowth)
            assertEquals(0, f.player.catHunger)
            assertEquals(0, f.player.catAttention)
        }
    }

    private class Fixture {
        val clock = MapClock(100)
        val flags = CollisionFlagMap()
        val npcs = NpcList()
        val events = EventBus()
        val repo = NpcRepository(clock, NpcRegistry(npcs, flags, events), npcs)
        val manager = CatPetManager(repo,
            clock, DefaultGameRandom(1), flags)
        val client = RecordingClient()
        var player = newPlayer()
        private fun newPlayer() = Player(client).apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = CoordGrid(3200, 3200, 0)
            currentMapClock = clock.cycle
        }
        init {
            for (x in 3192..3208 step 8) for (z in 3192..3208 step 8) flags.allocateIfAbsent(x, z, 0)
            with(CatPetScript(manager, repo)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
        fun savedVars() = player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        }
        fun login() { assertTrue(events.publish(SessionStateEvent.Login(player))) }
        fun logout() { assertTrue(events.publish(SessionStateEvent.Logout(player))) }
        fun reload() {
            val saved = savedVars()
            val attrs = player.attr.toPersistentMap()
            player = newPlayer().apply {
                vars.backing.putAll(saved)
                attr.putAllFromPersistence(attrs)
            }
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }
}
