package org.rsmod.content.quest.area.varrock.gertrudescat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.other.pets.cats.CatPetManager
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class GertrudesCatStateTest {
    @Test
    fun `all saved flag combinations retain journal state and unrelated kitten data across reload`() {
        for (mask in 0..3) {
            val f = Fixture()
            val found = mask and 1 != 0
            val advised = mask and 2 != 0
            f.player.attr.putAllFromPersistence(mapOf(
                FoundKey to found, SardinesKey to advised, CrateKey to 3, "pets.cat.growth" to 27,
            ))
            f.quest.quest.jumpToStage(f.player, GertrudesCatQuest.STAGE_PAID_KIDS)

            f.login(f.player)

            assertEquals(found, f.quest.foundFluffs.get(f.player))
            assertEquals(advised, f.quest.toldSardines.get(f.player))
            assertEquals(3, f.quest.kittenCrate.get(f.player))
            val saved = f.player.attr.toPersistentMap()
            assertFalse(saved.containsKey(FoundKey))
            assertFalse(saved.containsKey(SardinesKey))
            assertFalse(saved.containsKey(CrateKey))
            assertEquals(27, saved["pets.cat.growth"])
            assertJournals(f, f.player, found, advised)

            val restored = player().apply {
                vars.backing.putAll(f.player.vars.backing.filterKeys {
                    ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
                })
                attr.putAllFromPersistence(saved)
            }
            f.login(restored)
            assertJournals(f, restored, found, advised)
            assertEquals(3, f.quest.kittenCrate.get(restored))
        }
    }

    @Test
    fun `an explicit cleared flag does not prevent importing the other flag`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(FoundKey to true, SardinesKey to true))
        f.quest.foundFluffs.set(f.player, false)

        f.login(f.player)

        assertFalse(f.quest.foundFluffs.get(f.player))
        assertTrue(f.quest.toldSardines.get(f.player))
        f.player.attr.putAllFromPersistence(mapOf(FoundKey to true))
        f.login(f.player)
        assertFalse(f.quest.foundFluffs.get(f.player))
        assertFalse(f.player.attr.toPersistentMap().containsKey(FoundKey))
    }

    @Test
    fun `quest reset clears flags and crate selection without restoring stale progress`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(FoundKey to true, SardinesKey to true, CrateKey to 2))
        f.quest.quest.jumpToStage(f.player, GertrudesCatQuest.STAGE_GAVE_SARDINE)

        f.quest.quest.resetQuest(f.player)
        f.player.attr.putAllFromPersistence(mapOf(FoundKey to true, SardinesKey to true, CrateKey to 2))
        f.login(f.player)

        assertEquals(0, f.quest.stage(f.player))
        assertFalse(f.quest.foundFluffs.get(f.player))
        assertFalse(f.quest.toldSardines.get(f.player))
        assertEquals(-1, f.quest.kittenCrate.get(f.player))
        assertFalse(f.player.attr.toPersistentMap().containsKey(FoundKey))
        assertFalse(f.player.attr.toPersistentMap().containsKey(SardinesKey))
        assertFalse(f.player.attr.toPersistentMap().containsKey(CrateKey))
    }

    @Test
    fun `all six crate choices and the unassigned default survive legacy import and a new session`() {
        for (choice in -1..5) {
            val f = Fixture()
            f.player.attr.putAllFromPersistence(mapOf(CrateKey to choice))
            f.login(f.player)
            assertEquals(choice, f.quest.kittenCrate.get(f.player))
            assertFalse(f.player.attr.toPersistentMap().containsKey(CrateKey))

            val resumed = player().apply {
                vars.backing.putAll(f.player.vars.backing.filterKeys {
                    ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
                })
                attr.putAllFromPersistence(mapOf(CrateKey to 4))
            }
            f.login(resumed)
            assertEquals(choice, f.quest.kittenCrate.get(resumed))
            assertFalse(resumed.attr.toPersistentMap().containsKey(CrateKey))
        }
    }

    @Test
    fun `crate zero remains a real choice and shared flag writes do not suppress crate import`() {
        val f = Fixture()
        f.quest.foundFluffs.set(f.player, true)
        f.player.attr.putAllFromPersistence(mapOf(CrateKey to 5))
        f.login(f.player)
        assertEquals(5, f.quest.kittenCrate.get(f.player))

        val explicitZero = player().apply {
            VarPlayerIntMapSetter.set(this, "varp.gertrudes_cat_kitten_crate", 0)
            attr.putAllFromPersistence(mapOf(CrateKey to 5, FoundKey to true))
        }
        f.login(explicitZero)
        assertEquals(0, f.quest.kittenCrate.get(explicitZero))
        assertTrue(f.quest.foundFluffs.get(explicitZero))
        assertFalse(explicitZero.attr.toPersistentMap().containsKey(CrateKey))
    }

    @Test
    fun `fresh default and explicit reset cannot regain a stale crate on reload`() {
        val f = Fixture()
        f.login(f.player)
        assertEquals(-1, f.quest.kittenCrate.get(f.player))
        f.quest.kittenCrate.set(f.player, 0)
        f.quest.quest.resetQuest(f.player)
        val resumed = player().apply {
            vars.backing.putAll(f.player.vars.backing)
            attr.putAllFromPersistence(mapOf(CrateKey to 0))
        }
        f.login(resumed)
        assertEquals(-1, f.quest.kittenCrate.get(resumed))
    }

    @Test
    fun `fresh characters retain defaults and new progress is not downgraded by older false flags`() {
        val f = Fixture()
        assertFalse(f.quest.foundFluffs.get(f.player))
        assertFalse(f.quest.toldSardines.get(f.player))
        f.quest.foundFluffs.set(f.player, true)
        f.quest.toldSardines.set(f.player, true)
        f.player.attr.putAllFromPersistence(mapOf(FoundKey to false, SardinesKey to false))

        f.login(f.player)

        assertTrue(f.quest.foundFluffs.get(f.player))
        assertTrue(f.quest.toldSardines.get(f.player))
        assertEquals(-1, f.quest.kittenCrate.get(f.player))
    }

    @Test
    fun `progress and import markers occupy distinct permanent server-only bits`() {
        val f = Fixture()
        f.quest.quest.assertFieldsResolve()
        val varp = checkNotNull(ServerCacheManager.getVarp("varp.gertrudes_cat_progress".asRSCM()))
        assertEquals(VarpLifetime.Perm, varp.scope)
        assertEquals(VarpTransmitLevel.Never, varp.transmit)
        val bits = listOf("found_fluffs", "told_sardines", "found_fluffs_migrated", "told_sardines_migrated")
            .map { checkNotNull(ServerCacheManager.getVarbit("varbit.gertrudes_cat_$it".asRSCM())) }
        assertTrue(bits.all { it.varp == varp.id && it.startBit == it.endBit })
        assertEquals((0..3).toSet(), bits.map { it.startBit }.toSet())
    }

    private fun assertJournals(f: Fixture, player: Player, found: Boolean, advised: Boolean) {
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
        f.quest.quest.jumpToStage(player, GertrudesCatQuest.STAGE_PAID_KIDS)
        assertEquals(found, f.quest.questLog(access).contains("Maybe she is thirsty."))
        f.quest.quest.jumpToStage(player, GertrudesCatQuest.STAGE_GAVE_MILK)
        val journal = f.quest.questLog(access)
        assertEquals(advised, journal.contains("Gertrude says she loves"))
        assertEquals(!advised, journal.contains("Gertrude might know what she likes to eat."))
    }

    private class Fixture {
        private val clock = MapClock(100)
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val events = EventBus()
        private val cats = CatPetManager(
            NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs),
            clock, DefaultGameRandom(1), collision,
        )
        val quest = GertrudesCatQuest(cats)
        val player = player()

        init {
            with(quest) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun login(player: Player) { events.publish(SessionStateEvent.Login(player)) }
    }

    companion object {
        private const val FoundKey = "quest.quest_gertrudescat.FOUND_FLUFFS"
        private const val SardinesKey = "quest.quest_gertrudescat.TOLD_SARDINES"
        private const val CrateKey = "quest.quest_gertrudescat.KITTEN_CRATE"

        private fun player() = Player().apply { inv = invMap.getOrPut("inv.inv") }

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
