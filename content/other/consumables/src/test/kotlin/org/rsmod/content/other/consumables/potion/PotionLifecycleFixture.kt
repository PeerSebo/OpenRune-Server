package org.rsmod.content.other.consumables.potion

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.assertTrue
import org.rsmod.annotations.InternalApi
import org.rsmod.api.account.saver.PlayerSavePreparationHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.stat.stat
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.content.other.consumables.ConsumablesModule
import org.rsmod.content.other.consumables.heart.HeartScript
import org.rsmod.content.other.consumables.potion.moons.MoonsPotionTimerScript
import org.rsmod.content.other.consumables.potion.toa.ToaPotionTimerScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
internal class PotionLifecycleFixture {
    val clock = MapClock(1000)
    val injector = Guice.createInjector(ConsumablesModule(), object : AbstractModule() {
        override fun configure() { bind(MapClock::class.java).toInstance(clock) }
    })
    val effects = injector.getInstance(PotionEffectService::class.java)
    val divine = injector.getInstance(DivineEffectService::class.java)
    val prayer = injector.getInstance(PrayerRegenerationEffectService::class.java)
    val client = RecordingClient()
    val hits = mutableListOf<Int>()
    var player = newPlayer()
    private val events = EventBus().also { events ->
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(injector.getInstance(PotionEffectTimerScript::class.java)) {
            context.startup()
        }
        with(injector.getInstance(HeartScript::class.java)) {
            context.startup()
        }
        with(injector.getInstance(MoonsPotionTimerScript::class.java)) {
            context.startup()
        }
        with(injector.getInstance(ToaPotionTimerScript::class.java)) {
            context.startup()
        }
    }

    private fun newPlayer() = Player(client).apply {
        this@PotionLifecycleFixture.client.player = this
        inv = Inventory.create("inv.inv")
        worn = Inventory.create("inv.worn")
        coords = CoordGrid(3222, 3218)
        currentMapClock = clock.cycle
        processedMapClock = clock.cycle
        for (stat in ServerCacheManager.getStats().values) {
            val symbol = RSCM.getReverseMapping(RSCMType.STAT, stat.id)
            statMap.setBaseLevel(symbol, 99)
            statMap.setCurrentLevel(symbol, 99)
        }
    }

    fun at(cycle: Int) { clock.cycle = cycle; player.currentMapClock = cycle; player.processedMapClock = cycle }
    fun level(stat: String, current: Int) { player.statMap.setCurrentLevel(stat, current.toByte()) }
    fun drink(symbol: String) {
        val effect = PotionEffectRow.getRow(symbol)
        val access = access()
        assertTrue(effects.canApply(access, effect))
        effects.apply(access, effect)
    }

    fun prepare() {
        val hooks = injector.getInstance(Key.get(object : TypeLiteral<Set<PlayerSavePreparationHook>>() {}))
        hooks.single { it is ConsumablePotionSavePreparationHook }.prepare(player)
    }

    fun logout() {
        player.loggingOut = true
        assertTrue(events.publish(SessionStateEvent.Logout(player)))
    }

    fun death() {
        player.attr.removeIf { it.resetOnDeath }
        val hooks = injector.getInstance(Key.get(object : TypeLiteral<Set<PlayerDeathCleanupHook>>() {}))
        hooks.forEach { it.cleanup(player) }
    }

    fun saved() = Saved(
        player.vars.backing.filterKeys { ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm },
        player.attr.toPersistentMap(),
    )

    fun restore(saved: Saved, clock: Int) {
        this.clock.cycle = clock
        player = newPlayer().apply {
            vars.backing.putAll(saved.vars)
            attr.putAllFromPersistence(saved.attrs)
        }
        assertTrue(events.publish(SessionStateEvent.Login(player)))
    }

    fun timer(timer: String): Int? = player.timerMap[timer.asRSCM().toShort()]?.let {
        player.timerMap.extractExpiry(it)
    }

    fun pulse(timer: String) {
        val coroutine = player.launch {
            val access = ProtectedAccess(player, this, ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events },
            ))
            assertTrue(events.publish(access, PlayerTimerEvent.Normal(timer.asRSCM())))
        }
        assertTrue(coroutine.isIdle)
    }

    fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty().copy(
        getEventBus = { events },
        getInstantHitProcessor = {
            InstantPlayerHitProcessor { hit ->
                hits += hit.damage
                statMap.setCurrentLevel("stat.hitpoints", (stat("stat.hitpoints") - hit.damage).toByte())
            }
        },
    ))

    data class Saved(val vars: Map<Int, Int>, val attrs: Map<String, Any>)

    class RecordingClient : Client<Any, Any> {
        var player: Player? = null
        val messages = mutableListOf<Any>()
        val divineFrames = mutableListOf<List<Int>>()
        override fun write(message: Any) {
            messages += message
            player?.let { divineFrames += DIVINE_FIELDS.map { name -> it.vars[name] } }
        }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        val DIVINE_FIELDS = listOf(
            "varbit.divineattack_potion_time", "varbit.divinestrength_potion_time",
            "varbit.divinedefence_potion_time", "varbit.divinerange_potion_time",
            "varbit.divinemagic_potion_time", "varbit.divinecombat_potion_time",
            "varbit.divinebastion_potion_time", "varbit.divinebattlemage_potion_time",
        )
    }
}
