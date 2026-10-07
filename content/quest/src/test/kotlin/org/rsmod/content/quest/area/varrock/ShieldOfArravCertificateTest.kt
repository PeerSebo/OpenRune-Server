package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.rewards
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ShieldOfArravCertificateTest {
    @Test
    fun `partners combine exchanged halves and neither can collect another pair`() {
        val phoenix = Fixture(ShieldGivenLeftBit)
        val blackArm = Fixture(ShieldGivenRightBit)
        for (fixture in listOf(phoenix, blackArm)) {
            fixture.player.inv[0] = InvObj(SoaRefs.CertificateLeft)
            fixture.player.inv[1] = InvObj(SoaRefs.CertificateRight)
            fixture.combine()
            assertEquals(1, fixture.player.inv.count(SoaRefs.Certificate))
            fixture.curator()
            assertEquals(0, fixture.player.inv.count(SoaRefs.CertificateLeft))
            assertEquals(0, fixture.player.inv.count(SoaRefs.CertificateRight))
            assertEquals(1, fixture.player.inv.count(SoaRefs.Certificate))
        }
    }

    @Test
    fun `combining keeps a spare half available for the other partner`() {
        val fixture = Fixture(ShieldGivenLeftBit)
        fixture.player.inv[0] = InvObj(SoaRefs.CertificateLeft)
        fixture.player.inv[1] = InvObj(SoaRefs.CertificateRight)
        fixture.player.inv[2] = InvObj(SoaRefs.CertificateLeft)

        fixture.combine()
        fixture.curator()

        assertEquals(1, fixture.player.inv.count(SoaRefs.Certificate))
        assertEquals(1, fixture.player.inv.count(SoaRefs.CertificateLeft))
        assertEquals(0, fixture.player.inv.count(SoaRefs.CertificateRight))
    }

    @Test
    fun `banking a complete certificate does not unlock replacement halves`() {
        val fixture = Fixture(ShieldGivenRightBit)
        fixture.player.invMap.getOrPut("inv.bank")[0] = InvObj(SoaRefs.Certificate)

        fixture.curator()

        assertTrue(fixture.player.inv.isEmpty())
        assertEquals(1, fixture.player.invMap.getOrPut("inv.bank").count(SoaRefs.Certificate))
    }

    @Test
    fun `lost certificates can still be replaced before they are combined`() {
        val fixture = Fixture(ShieldGivenLeftBit)

        fixture.curator()

        assertEquals(2, fixture.player.inv.count(SoaRefs.CertificateLeft))
        assertEquals(0, fixture.player.inv.count(SoaRefs.CertificateRight))
    }

    @Test
    fun `stale combine without the other half preserves the remaining half`() {
        val fixture = Fixture(ShieldGivenLeftBit)
        fixture.player.inv[0] = InvObj(SoaRefs.CertificateLeft)
        fixture.combine()
        assertEquals(1, fixture.player.inv.count(SoaRefs.CertificateLeft))
        assertEquals(0, fixture.player.inv.count(SoaRefs.Certificate))
    }

    private class Fixture(given: Int) {
        private val events = EventBus()
        val player = Player().apply {
            inv = invMap.getOrPut("inv.inv")
            worn = Inventory.create("inv.worn")
            currentMapClock = 100
            processedMapClock = 100
        }
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events },
            getAlignment = { TextAlignment() },
        )
        private val quest = Quest(
            id = 0,
            key = SoaRefs.QuestKey,
            rowID = 0,
            displayName = "Shield of Arrav",
            mapElement = null,
            startCoord = null,
            maxSteps = SoaStage.Complete,
            questPoints = 1,
            questVarp = SoaRefs.QuestVarp,
            questVarbit = SoaRefs.StageVarbit,
            rewards = rewards {},
            itemDisplay = null,
        )

        init {
            with(ShieldOfArravFinale(quest)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).register()
            }
            quest.jumpToStage(player, SoaStage.Started)
            VarPlayerIntMapSetter.set(player, "varbit.soa_shield_given", given)
        }

        fun combine() {
            val left = checkNotNull(ServerCacheManager.getItem(SoaRefs.CertificateLeft.asRSCM()))
            val right = checkNotNull(ServerCacheManager.getItem(SoaRefs.CertificateRight.asRSCM()))
            finish(HeldUEvents.Type(left, 0, right, 1))
        }

        fun curator() {
            finish(NpcEvents.Op1(Npc(SoaRefs.Curator, CoordGrid(3253, 3448))))
        }

        private fun finish(event: SuspendEvent<ProtectedAccess>) {
            val coroutine = player.launch {
                assertTrue(events.publish(ProtectedAccess(player, this, context), event))
            }
            var steps = 0
            while (coroutine.isSuspended && steps++ < 20) advance(coroutine)
            assertTrue(coroutine.isIdle)
        }

        private fun advance(coroutine: GameCoroutine) {
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
            val button = when {
                player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                player.ui.containsModal("interface.objectbox") -> "component.objectbox:universe"
                player.ui.containsModal("interface.objectbox_double") -> "component.objectbox_double:pausebutton"
                else -> error("Unexpected dialogue interface: ${player.ui.modals}")
            }
            coroutine.resumeWith(ResumePauseButtonInput(button, -1))
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
