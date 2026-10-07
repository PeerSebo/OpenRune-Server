package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RewardPacksScriptTest {
    @Test
    fun `fixed packs open through their native action and grant all hundred items`() {
        for ((pack, contents) in listOf(
            "obj.pack_amylase" to "obj.amylase",
            "obj.pack_softclay" to "obj.cert_softclay",
            "obj.pack_softclay_guild" to "obj.cert_softclay",
            "obj.star_pack_softclay" to "obj.cert_softclay",
            "obj.shark_lure_pack" to "obj.shark_lure",
        )) {
            val f = Fixture(pack)
            for (slot in 1 until f.player.inv.size) f.player.inv[slot] = InvObj("obj.pot_empty")
            f.open()
            assertEquals(0, f.player.inv.count(pack))
            assertEquals(100, f.player.inv.count(contents))
            if (contents == "obj.cert_softclay") {
                assertTrue(checkNotNull(ServerCacheManager.getItem(contents.asRSCM())).certtemplate > 0)
                assertEquals(0, f.player.inv.count("obj.softclay"))
            }
            assertEquals(27, f.player.inv.count("obj.pot_empty"))
        }
    }

    @Test
    fun `fixed pack stack overflow leaves the whole inventory unchanged`() {
        for ((pack, contents) in RewardPacksScript.fixedPacks) {
            val f = Fixture(pack)
            f.player.inv[1] = InvObj(contents, Int.MAX_VALUE - 99)
            val before = f.player.inv.objs.toList()
            f.open()
            assertEquals(before, f.player.inv.objs.toList(), pack)
        }
    }

    @Test
    fun `gem bag rolls forty gems and grants the noted variants`() {
        val f = Fixture("obj.reward_gem_bag_guild", ScriptedRandom(intArrayOf(0, 100, 169, 193, 199)))
        f.open()
        assertEquals(0, f.player.inv.count("obj.reward_gem_bag_guild"))
        for (gem in listOf("sapphire", "emerald", "ruby", "diamond", "dragonstone")) {
            val noted = "obj.cert_uncut_$gem"
            assertEquals(8, f.player.inv.count(noted))
            assertTrue(checkNotNull(ServerCacheManager.getItem(noted.asRSCM())).certtemplate > 0)
            assertEquals(0, f.player.inv.count("obj.uncut_$gem"))
        }
    }

    @Test
    fun `insufficient gem space preserves the bag and all other items`() {
        val f = Fixture("obj.reward_gem_bag_guild", ScriptedRandom(intArrayOf(0, 100, 169, 193, 199)))
        for (slot in 1..26) f.player.inv[slot] = InvObj("obj.pot_empty")
        val before = f.player.inv.objs.toList()
        f.open()
        assertEquals(before, f.player.inv.objs.toList())
    }

    @Test
    fun `gem weights cover two hundred normal results and a separate rare onyx roll`() {
        val random = ScriptedRandom(IntArray(200) { it })
        val counts = List(200) { RewardPacksScript.rollGem(random) }.groupingBy { it }.eachCount()
        assertEquals(mapOf(
            "obj.cert_uncut_sapphire" to 100,
            "obj.cert_uncut_emerald" to 69,
            "obj.cert_uncut_ruby" to 24,
            "obj.cert_uncut_diamond" to 6,
            "obj.cert_uncut_dragonstone" to 1,
        ), counts)
        assertEquals("obj.cert_uncut_onyx", RewardPacksScript.rollGem(ScriptedRandom(onyxAt = setOf(0))))
    }

    @Test
    fun `only a successfully delivered onyx updates the collection log`() {
        for (overflow in listOf(false, true)) {
            val f = Fixture("obj.reward_gem_bag_guild", ScriptedRandom(onyxAt = setOf(0)))
            if (overflow) f.player.inv[1] = InvObj("obj.cert_uncut_onyx", Int.MAX_VALUE)
            val before = f.player.inv.objs.toList()
            f.open()
            if (overflow) {
                assertEquals(before, f.player.inv.objs.toList())
                assertEquals(0, f.player.collectionTransmit.count("obj.uncut_onyx"))
            } else {
                assertEquals(1, f.player.inv.count("obj.cert_uncut_onyx"))
                assertEquals(39, f.player.inv.count("obj.cert_uncut_sapphire"))
                assertEquals(1, f.player.collectionTransmit.count("obj.uncut_onyx"))
            }
        }
    }

    @Test
    fun `a stale open event cannot consume a different item or award contents`() {
        val f = Fixture("obj.pack_amylase")
        f.player.inv[0] = InvObj("obj.pot_empty")
        val before = f.player.inv.objs.toList()
        f.open()
        assertEquals(before, f.player.inv.objs.toList())
    }

    private class Fixture(pack: String, random: GameRandom = ScriptedRandom()) {
        val player = Player().apply { inv = Inventory.create("inv.inv"); inv[0] = InvObj(pack) }
        private val type = checkNotNull(ServerCacheManager.getItem(pack.asRSCM()))
        private val event = HeldObjEvents.Op1(0, InvObj(pack), type, player.inv)
        private val events = EventBus()
        private val coroutine = GameCoroutine("reward-pack-test")
        private val access = ProtectedAccess(player, coroutine,
            ProtectedAccessContextFactory.empty().copy(getEventBus = { events }, getRandom = { random }))

        init {
            assertEquals("Open", type.interfaceOptions[0], pack)
            player.activeCoroutine = coroutine
            with(RewardPacksScript()) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun open() {
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            checkNotNull(completion).getOrThrow()
        }
    }

    private class ScriptedRandom(
        private val normals: IntArray = intArrayOf(0),
        private val onyxAt: Set<Int> = emptySet(),
    ) : GameRandom {
        private var gem = 0
        private var normal = 0
        override fun of(maxExclusive: Int): Int = when (maxExclusive) {
            100_000_000 -> if (gem++ in onyxAt) 0 else 1
            200 -> normals[normal++ % normals.size]
            else -> error("Unexpected random bound $maxExclusive")
        }
        override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive
        override fun randomDouble(): Double = 0.0
    }

    companion object {
        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
