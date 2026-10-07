package org.rsmod.content.skills.woodcutting

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.skills.woodcutting.BeaverPet.rollBeaver
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BeaverPetTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @BeforeEach
    fun installInventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach
    fun restoreInventoryServices() {
        restore.asReversed().forEach { it() }
        restore.clear()
    }

    @Test
    fun `winning roll puts exactly one pet in a free inventory slot`() {
        val fixture = Fixture()
        fixture.access(0).rollBeaver("obj.logs", fixture.repository)
        assertEquals(BeaverPet.PET_OBJ.asRSCM(), fixture.player.inv[0]?.id)
        assertEquals(1, fixture.player.inv[0]?.count)
        assertTrue(fixture.repository.findAll(fixture.player.coords).none())
    }

    @Test
    fun `winning roll with the last slot filled retains logs and creates an owned timed pet drop`() {
        val fixture = Fixture()
        val log = InvObj(checkNotNull(ServerCacheManager.getItem("obj.logs".asRSCM())))
        fixture.player.inv[0] = log
        fixture.access(0).rollBeaver("obj.logs", fixture.repository)
        assertEquals(log, fixture.player.inv[0])
        val pet = fixture.repository.findAll(fixture.player.coords).single()
        assertEquals(BeaverPet.PET_OBJ.asRSCM(), pet.type)
        assertEquals(1, pet.count)
        assertEquals(fixture.player.observerUUID, pet.nullableOwnerId)
        assertEquals(fixture.player.observerUUID, pet.nullableReceiverId)
        assertTrue(pet.isPrivate)

        fixture.clock.cycle += 199
        processDurations.invoke(fixture.repository)
        assertEquals(pet, fixture.repository.findAll(fixture.player.coords).single())
        fixture.clock.tick()
        processDurations.invoke(fixture.repository)
        assertTrue(fixture.repository.findAll(fixture.player.coords).none())
    }

    @Test
    fun `failed roll creates neither a pet item nor a ground drop`() {
        val fixture = Fixture()
        fixture.access(1).rollBeaver("obj.logs", fixture.repository)
        assertTrue(fixture.player.inv.isEmpty())
        assertTrue(fixture.repository.findAll(fixture.player.coords).none())
    }

    private class Fixture {
        val clock = MapClock(100)
        val player = Player().apply {
            inv = Inventory(InventoryServerType(size = 1), arrayOfNulls(1))
            observerUUID = 123L
            currentMapClock = clock.cycle
            coords = CoordGrid(3200, 3200)
            statMap.setBaseLevel("stat.woodcutting", 99)
        }
        val repository = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))

        fun access(roll: Int) = ProtectedAccess(
            player,
            GameCoroutine(),
            ProtectedAccessContext(
                getRandom = { object : GameRandom {
                    override fun of(maxExclusive: Int): Int {
                        assertEquals(317647 - 99 * 25, maxExclusive)
                        return roll
                    }
                    override fun of(minInclusive: Int, maxInclusive: Int): Int = error("unused")
                    override fun randomDouble(): Double = error("unused")
                } },
                getEventBus = { error("unused") },
                getNpcList = { error("unused") },
                getPlayerList = { error("unused") },
                getCollision = { error("unused") },
                getAreaChecker = { error("unused") },
                getAlignment = { error("unused") },
                getLocInteractions = { error("unused") },
                getNpcInteractions = { error("unused") },
                getPlayerInteractions = { error("unused") },
                getHeldInteractions = { error("unused") },
                getWornInteractions = { error("unused") },
                getMusicPlayer = { error("unused") },
                getMarketPrices = { error("unused") },
                getInstantHitProcessor = { error("unused") },
                getTeleportValidator = { error("unused") },
                getWorldRepo = { error("unused") },
                getHitModifier = { error("unused") },
            ),
        )
    }

    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }

    private companion object {
        val processDurations = ObjRepository::class.java.declaredMethods.single {
            it.name.startsWith("processDurations") && it.parameterCount == 0
        }.apply { isAccessible = true }
    }
}
