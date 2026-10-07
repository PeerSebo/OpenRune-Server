package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.game.process.npc.NpcPostTickProcess
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.content.other.pets.cats.CatColour
import org.rsmod.content.other.pets.cats.CatPet
import org.rsmod.content.other.pets.cats.CatPetManager
import org.rsmod.content.other.pets.cats.CatStage
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(InternalApi::class)
class PetFollowingTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `cat stops its rat route and returns to a stationary owner`() {
        val f = Fixture()
        val cat = f.cat()
        f.cats.setChasing(f.player, true)
        cat.coords = f.player.coords.translateX(4)
        cat.walk(cat.coords.translateZ(4))
        cat.resetFaceEntity()
        f.followers.tick(f.player)
        assertEquals(cat.coords.translateZ(4), cat.routeDestination.lastOrNull())

        f.cats.setChasing(f.player, false)
        assertTrue(cat.routeDestination.isEmpty())
        assertEquals(NpcMode.None, cat.mode)
        assertTrue(cat.isFacingPlayer)
        repeat(8) { f.cycle(cat) }

        assertEquals(1, cat.coords.chebyshevDistance(f.player.coords))
        assertTrue(cat.routeDestination.isEmpty())
        assertSame(cat, f.cats.following(f.player))
    }

    @Test fun `distant cats and catalogue pets teleport beside their owner with protocol and zone updates`() {
        for (catalogue in listOf(false, true)) {
            val f = Fixture()
            val pet = if (catalogue) f.cataloguePet() else f.cat()
            val oldZone = pet.lastProcessedZone
            val protocol = RecordingInfo()
            pet.infoProtocol = protocol
            f.player.coords = CoordGrid(3232, 3232, 0)
            f.player.previousCoords = f.player.coords

            f.followers.tick(f.player)
            f.postTick.process()

            assertEquals(1, pet.coords.chebyshevDistance(f.player.coords))
            assertEquals(listOf(pet.coords), protocol.teleports)
            assertEquals(ZoneKey.from(pet.coords), pet.lastProcessedZone)
            assertFalse(pet in f.registry.findAll(oldZone).toList())
            assertTrue(pet in f.registry.findAll(pet.lastProcessedZone).toList())
            assertSame(pet, f.followers.follower(f.player))
        }
    }

    @Test fun `pets catch up across floors instead of attempting an impossible route`() {
        val f = Fixture()
        val cat = f.cat()
        f.player.coords = CoordGrid(3200, 3200, 1)
        f.followers.tick(f.player)
        assertEquals(1, cat.level)
        assertEquals(1, cat.coords.chebyshevDistance(f.player.coords))
        assertTrue(cat.pendingTeleport)
        assertTrue(cat.routeDestination.isEmpty())
    }

    @Test fun `blocked pet recovers after five immobile cycles`() {
        val f = Fixture()
        val cat = f.cat()
        cat.coords = CoordGrid(3206, 3200, 0)
        for (x in 3205..3207) for (z in 3199..3201) {
            if (x != 3206 || z != 3200) f.flags.add(x, z, 0, CollisionFlag.BLOCK_WALK)
        }
        repeat(4) { f.cycle(cat) }
        assertEquals(CoordGrid(3206, 3200, 0), cat.coords)
        f.cycle(cat)
        assertEquals(1, cat.coords.chebyshevDistance(f.player.coords))
        assertEquals(0, f.player.vars["varbit.pet_follower_stuck"])
    }

    @Test fun `adjacent idle pet cancels stale routes without shuffling`() {
        val f = Fixture()
        val cat = f.cat()
        val start = cat.coords
        cat.walk(start.translateX(4))
        f.followers.tick(f.player)
        assertTrue(cat.routeDestination.isEmpty())
        f.cycle(cat)
        assertEquals(start, cat.coords)
    }

    @Test fun `running owner leaves the pet the last tile vacated rather than a two tile gap`() {
        val f = Fixture()
        val cat = f.cat()
        f.player.previousCoords = f.player.coords
        f.player.lastProcessedCoord = f.player.coords.translateX(1)
        f.player.coords = f.player.coords.translateX(2)
        f.player.lastMovement = f.clock.cycle
        f.followers.tick(f.player)
        assertEquals(f.player.lastProcessedCoord, cat.routeDestination.lastOrNull())
    }

    @Test fun `short teleport does not send the pet back to the old owner position`() {
        val f = Fixture()
        val cat = f.cat()
        f.player.coords = f.player.coords.translateX(8)
        f.followers.tick(f.player)
        val destination = checkNotNull(cat.routeDestination.lastOrNull())
        assertEquals(1, destination.chebyshevDistance(f.player.coords))
    }

    @Test fun `pet already on the running owners trail drops an obsolete route`() {
        val f = Fixture()
        val cat = f.cat()
        f.player.previousCoords = cat.coords.translateX(-1)
        f.player.lastProcessedCoord = cat.coords
        f.player.coords = cat.coords.translateX(1)
        f.player.lastMovement = f.clock.cycle
        cat.walk(cat.coords.translateZ(3))
        f.followers.tick(f.player)
        assertTrue(cat.routeDestination.isEmpty())
    }

    @Test fun `call follower works for a cat without catalogue follower state`() {
        val f = Fixture()
        val cat = f.cat()
        cat.coords = cat.coords.translateX(7)
        assertTrue(f.followers.call(f.player))
        assertEquals(1, cat.coords.chebyshevDistance(f.player.coords))
    }

    @Test fun `another players follower is never moved or adopted`() {
        val f = Fixture()
        val cat = f.cat()
        val other = Player().apply { slotId = 2; uuid = 2L; assignUid() }
        cat.assignSpawnOwner(other, f.clock.cycle)
        val start = cat.coords
        f.player.coords = f.player.coords.translateX(30)
        f.followers.tick(f.player)
        assertEquals(start, cat.coords)
        assertNull(f.followers.follower(f.player))
    }

    @Test fun `catch up avoids blocked and unallocated adjacent tiles`() {
        val f = Fixture()
        val cat = f.cat()
        f.player.coords = CoordGrid(3240, 3240, 0)
        f.flags.allocateIfAbsent(3240, 3240, 0)
        f.flags.add(3241, 3240, 0, CollisionFlag.BLOCK_WALK)
        f.followers.tick(f.player)
        assertEquals(CoordGrid(3240, 3241, 0), cat.coords)
    }

    private class Fixture {
        val clock = MapClock(100)
        val flags = CollisionFlagMap()
        val npcs = NpcList()
        val events = EventBus()
        val registry = NpcRegistry(npcs, flags, events)
        val repo = NpcRepository(clock, registry, npcs)
        val followers = PetFollowers(repo, npcs, clock, flags)
        val cats = CatPetManager(repo, clock, DefaultGameRandom(1), flags)
        val movement = NpcMovementProcessor(flags, StepFactory(flags), events)
        val postTick = NpcPostTickProcess(npcs, registry, NpcFaceSquareProcessor(),
            GameExceptionHandler { t, _ -> throw AssertionError(t) })
        val player = Player().apply {
            slotId = 1; uuid = 1L; assignUid()
            coords = CoordGrid(3200, 3200, 0)
            previousCoords = coords
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
        }
        init {
            for (level in 0..1) for (x in 3192..3232 step 8) for (z in 3192..3232 step 8) {
                flags.allocateIfAbsent(x, z, level)
            }
        }
        fun cat() = cats.spawn(player, CatPet(CatStage.CAT, CatColour.DEFAULT))
        fun cataloguePet(): Npc {
            followers.spawn(player, Pets.all.first().base)
            return checkNotNull(followers.follower(player))
        }
        fun cycle(npc: Npc) {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            npc.currentMapClock = clock.cycle
            npc.processedMapClock = clock.cycle
            npc.previousCoords = npc.coords
            movement.process(npc)
            followers.tick(player)
            postTick.process()
        }
    }

    private class RecordingInfo : NpcInfoProtocol by NoopNpcInfo {
        val teleports = mutableListOf<CoordGrid>()
        override fun teleport(x: Int, z: Int, level: Int, jump: Boolean) {
            teleports += CoordGrid(x, z, level)
        }
    }
}
