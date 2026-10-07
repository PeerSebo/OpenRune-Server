package org.rsmod.content.interfaces.settings

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.ifOpenTop
import org.rsmod.content.interfaces.settings.scripts.AllSettingsScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class SettingsCategoryStateTest {
    @Test fun `category zero and search selection survive close and reset when reopened`() {
        val f = Fixture()
        assertFalse(Category in f.player.vars)
        f.click("component.settings_side:settings_open")
        assertEquals(0, f.player.vars[Category])
        assertTrue(Category in f.player.vars)
        f.click("component.settings:categories_clickzone", 3)
        assertEquals(3, f.player.vars[Category])
        f.click("component.settings:searchbar_image")
        assertEquals(-1, f.player.vars[Category])
        assertEquals(1, f.player.vars["varbit.floater_is_searching"])
        f.click("component.settings:close")
        assertFalse(f.player.ui.containsOverlay("interface.settings"))
        assertEquals(-1, f.player.vars[Category])
        f.click("component.settings_side:settings_open")
        assertEquals(0, f.player.vars[Category])
        assertEquals(0, f.player.vars["varbit.floater_is_searching"])
        assertTrue(f.player.ui.containsOverlay("interface.settings"))
    }

    @Test fun `settings session selection is not restored with permanent account preferences`() {
        val f = Fixture()
        f.click("component.settings_side:settings_open")
        f.click("component.settings:searchbar_image")
        val restored = Player().apply {
            vars.backing.putAll(f.player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }
        assertFalse(Category in restored.vars)
        val type = checkNotNull(ServerCacheManager.getVarp(Category.asRSCM()))
        assertEquals(VarpLifetime.Temp, type.scope)
        assertEquals(VarpTransmitLevel.Never, type.transmit)
    }

    private class Fixture {
        val player = Player().apply {
            client = NoopClient
            ifOpenTop("interface.toplevel_osrs_stretch")
        }
        private val events = EventBus()
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })

        init {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null) as Unsafe
            }
            val factory = unsafe.allocateInstance(ProtectedAccessContextFactory::class.java)
                as ProtectedAccessContextFactory
            with(AllSettingsScript(ProtectedAccessLauncher(factory))) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun click(symbol: String, sub: Int = -1) {
            val component = ServerCacheManager.fromComponent(symbol.asRSCM())
            var outcome: Result<Unit>? = null
            val action: suspend () -> Unit = {
                val access = ProtectedAccess(player, GameCoroutine(), context)
                assertTrue(events.publish(access, IfOverlayButton(component, sub, null, IfButtonOp.Op1)))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { outcome = result }
            })
            checkNotNull(outcome).getOrThrow()
        }
    }

    companion object {
        private const val Category = "varp.settings_selected_category"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
