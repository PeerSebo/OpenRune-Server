package org.rsmod.content.quest.area.lumbridge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.events.interact.ObjEvents
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrutusRegressionTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `Brutus has his native examine and published boss kill counter`() {
        val bull = checkNotNull(ServerCacheManager.getNpc(IdesOfMilkRefs.Bull.asRSCM()))
        assertEquals("Doesn't skip leg day.", bull.examine)
        assertEquals("varp.total_cowboss_kills".asRSCM(), bull.paramOrNull(BaseParams.killcount_varp)?.id)
        assertEquals(true, bull.paramOrNull(BaseParams.killcount_notify))
    }

    @Test fun `public postquest Brutus rejects melee ranged and magic and loses no health`() {
        val f = Fixture()
        val pen = f.npc(IdesOfMilkRefs.PenBull, IdesOfMilkCoords.BullSpawn)
        assertEquals(IdesOfMilkRefs.Bull.asRSCM(), pen.type.transforms!![22])
        assertInstanceOf(NpcAttackValidateResult.Deny::class.java, f.gate.validate(f.player, pen))
        val hp = pen.hitpoints
        for (style in listOf(HitType.Melee, HitType.Ranged, HitType.Magic)) {
            f.land(pen, pen.queueHit(f.player, 5, style, 20, NpcHitModifier {}))
            assertEquals(hp, pen.hitpoints, "$style must not damage the scenery multinpc")
            assertFalse("queue.death" in pen.queueList)
        }
    }

    @Test fun `only the owning player in the live instance can fight Brutus`() {
        val f = Fixture()
        val bull = f.npc(IdesOfMilkRefs.Bull, f.player.coords)
        f.gate.register(bull, f.player, f.region)
        assertEquals(NpcAttackValidateResult.Pass, f.gate.validate(f.player, bull))
        val stranger = Player().apply { coords = f.player.coords }
        assertInstanceOf(NpcAttackValidateResult.Deny::class.java, f.gate.validate(stranger, bull))
        val hit = bull.queueHit(f.player, 5, HitType.Ranged, 10, NpcHitModifier {})
        val hp = bull.hitpoints
        f.land(bull, hit)
        assertEquals(hp - 10, bull.hitpoints)
        f.player.coords = IdesOfMilkCoords.FightExit
        assertInstanceOf(NpcAttackValidateResult.Deny::class.java, f.gate.validate(f.player, bull))
        f.land(bull, hit)
        assertEquals(hp - 10, bull.hitpoints, "a projectile landing after exit must be discarded")
        f.player.coords = bull.coords
        f.gate.remove(bull)
        f.land(bull, hit)
        assertEquals(hp - 10, bull.hitpoints, "a closed encounter must stay protected")
    }

    @Test fun `unrelated NPCs retain normal attack permission`() {
        val f = Fixture()
        assertEquals(NpcAttackValidateResult.Pass, f.gate.validate(f.player, f.npc("npc.cow", f.player.coords)))
    }

    @Test fun `instance cow filter clears the spawn gate including later wandering`() {
        val nearCow = Npc("npc.cow", IdesOfMilkCoords.FightEntry.translateX(-1))
        val original = nearCow.coords
        assertFalse(BrutusScenery.shouldClone(nearCow))
        assertEquals(original, nearCow.coords, "public world cows are only inspected")
        val farCow = Npc("npc.cow", CoordGrid(3258, 3270))
        assertTrue(BrutusScenery.shouldClone(farCow))
        for (dx in -farCow.wanderRange..farCow.wanderRange) {
            for (dz in -farCow.wanderRange..farCow.wanderRange) {
                assertFalse(BrutusScenery.nearEntrance(farCow.coords.translate(dx, dz), farCow.size))
            }
        }
        assertFalse(BrutusScenery.shouldClone(Npc(IdesOfMilkRefs.PenBull, original)))
    }

    @Test fun `native Bury op grants forty Prayer XP once without needing inventory space`() {
        val f = Fixture()
        val bones = checkNotNull(ServerCacheManager.getItem(BullBonesScript.Bones.asRSCM()))
        assertEquals("Bury", bones.options.getOpOrNull(2))
        assertTrue((0..4).none { bones.options.getOpOrNull(it) == "Take" })
        val ground = Obj.fromOwner(f.player, f.player.coords, bones, 1)
        assertTrue(f.objs.add(ground, 100))
        val logs = checkNotNull(ServerCacheManager.getItem("obj.logs".asRSCM()))
        f.player.inv.objs.fill(InvObj(logs, 1))
        val before = f.player.inv.objs.toList()
        val outcomes = mutableListOf<SkillingActionCompleteEvent>()
        f.events.subscribeUnbound(SkillingActionCompleteEvent::class.java) { outcomes += this }
        with(BullBonesScript(f.objs)) { f.context.startup() }
        complete { assertTrue(f.events.publish(f.access(), ObjEvents.Op3(ground))) }
        assertEquals(400, f.player.statMap.getFineXP("stat.prayer"))
        assertEquals(before, f.player.inv.objs.toList())
        assertEquals(0, f.objRegistry.count())
        assertEquals(1, outcomes.size)
        f.player.currentMapClock += 3
        complete { f.events.publish(f.access(), ObjEvents.Op3(ground)) }
        assertEquals(400, f.player.statMap.getFineXP("stat.prayer"))
        assertEquals(1, outcomes.size)
        assertNotNull(PlayerObjTakeValidator(setOf(BullBonesTakeGate())).validate(f.player, ground, bones))
    }

    @Test fun `a remote or private bone cannot be buried and stale ground items grant nothing`() {
        val f = Fixture()
        val script = BullBonesScript(f.objs)
        val owner = Player().apply { observerUUID = 2 }
        val privateBone = Obj.fromOwner(owner, f.player.coords, BullBonesScript.Bones, 1)
        assertTrue(f.objs.add(privateBone, 100))
        assertFalse(script.consume(f.player, privateBone))
        val distant = Obj.fromOwner(f.player, f.player.coords.translateX(3), BullBonesScript.Bones, 1)
        assertTrue(f.objs.add(distant, 100))
        assertFalse(script.consume(f.player, distant))
        val vanished = Obj.fromOwner(f.player, f.player.coords, BullBonesScript.Bones, 1)
        assertFalse(script.consume(f.player, vanished))
        assertEquals(2, f.objRegistry.count())
    }

    private class Fixture {
        val events = EventBus()
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val gate = BrutusCombatGate()
        val players = PlayerList()
        val npcs = NpcList()
        val flags = CollisionFlagMap()
        val registry = NpcRegistry(npcs, flags, events)
        val clock = MapClock(100)
        val updates = ZoneUpdateMap()
        val objRegistry = ObjRegistry(updates)
        val objs = ObjRepository(clock, objRegistry)
        val world = WorldRepository(updates)
        val region = Region(CoordGrid(12000, 12000), CoordGrid(12319, 12319), 1, 0)
        val player = Player().apply {
            slotId = 1; uuid = 1; observerUUID = 1; assignUid()
            coords = CoordGrid(12050, 12050); currentMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        init {
            players[1] = player
            with(BrutusCombatScript(gate, players)) { context.startup() }
        }
        fun npc(type: String, coords: CoordGrid): Npc {
            flags.allocateIfAbsent(coords.x, coords.z, coords.level)
            return Npc(type, coords).also { registry.add(it) }
        }
        fun land(npc: Npc, hit: Hit) {
            val processor = StandardNpcHitProcessor(players, events, emptySet(), world)
            val access = StandardNpcAccess(npc, GameCoroutine(), StandardNpcAccessContext(
                { DefaultGameRandom(1) }, { NpcHitModifier {} }, { processor },
            ))
            with(processor) { access.process(hit) }
        }
        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { DefaultGameRandom(1) }, getEventBus = { events }, getNpcList = { npcs },
            getPlayerList = { players }, getCollision = { flags }, getAreaChecker = { error("unused") },
            getAlignment = { error("unused") }, getLocInteractions = { error("unused") },
            getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") },
            getMusicPlayer = { error("unused") }, getMarketPrices = { error("unused") },
            getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
            getWorldRepo = { world }, getHitModifier = { NoopPlayerHitModifier },
        ))
    }

    private fun complete(block: suspend () -> Unit) {
        var finished = false
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); finished = true }
        })
        assertTrue(finished, "Expected a non-delaying ground interaction")
    }
}
