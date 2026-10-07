package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("QuestRequirements")
class FoundryKovacViewTest {
    private lateinit var originalPolicy: QuestRequirementPolicy

    @BeforeEach fun rememberPolicy() { originalPolicy = QuestRequirements.activePolicy() }

    @AfterEach fun restorePolicy() { QuestRequirements.install(originalPolicy) }

    @Test
    fun `assumed quest completion exposes native shop and commission options without saving quest progress`() {
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
        val f = Fixture()
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varbit.sleeping_giants", 5)
        val savedVars = player.vars.backing.toMap()
        f.events.publish(NpcStateEvents.Spawn(f.kovac))
        assertEquals(FoundryKovacViewScript.UNLOCKED_KOVAC.asRSCM(), f.kovac.visType.id)
        assertEquals(listOf(FoundryKovacViewScript.UNLOCKED_KOVAC.asRSCM()), f.info.transforms)
        assertEquals(savedVars, player.vars.backing.toMap())
        assertEquals(5, player.vars["varbit.sleeping_giants"])
        assertEquals(FoundryKovacViewScript.KOVAC.asRSCM(), f.kovac.type.id)
    }

    @Test
    fun `respect progress still exposes optional minigame services`() {
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        val f = Fixture()
        f.events.publish(NpcStateEvents.Spawn(f.kovac))
        assertEquals(FoundryKovacViewScript.UNLOCKED_KOVAC.asRSCM(), f.kovac.visType.id)
        assertEquals(1, f.info.transforms.size)
        assertEquals(1, f.info.animationTypes.size)
    }

    @Test
    fun `completed appearance keeps the parent idle and movement sequences without changing wandering`() {
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
        val f = Fixture()
        val originalMode = f.kovac.mode
        val originalRange = f.kovac.wanderRange
        val originalRestrictions = f.kovac.moveRestrict
        f.events.publish(NpcStateEvents.Spawn(f.kovac))
        val animations = f.info.animationTypes.single()
        assertSame(f.kovac.type, animations)
        assertEquals(4670, animations.standAnim)
        assertEquals(6372, animations.walkAnim)
        assertEquals(6372, animations.rotateBackAnim)
        assertEquals(6371, animations.walkLeftAnim)
        assertEquals(6371, animations.walkRightAnim)
        assertEquals(originalMode, f.kovac.mode)
        assertEquals(originalRange, f.kovac.wanderRange)
        assertEquals(originalRestrictions, f.kovac.moveRestrict)
        assertFalse(f.kovac.movementLocked)
    }

    @Test
    fun `virtual completion selections never gate minigame services`() {
        for (completions in listOf(emptySet(), setOf("quest_ernest"), setOf("quest_sleepinggiants"))) {
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.VirtualCompletions, completions))
            val f = Fixture()
            f.events.publish(GameLifecycle.Startup)
            assertEquals(FoundryKovacViewScript.UNLOCKED_KOVAC.asRSCM(), f.kovac.visType.id)
        }
    }

    @Test
    fun `quest actors and copies outside the normal mapped spawn retain native definitions`() {
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
        val f = Fixture()
        val other = Npc(FoundryKovacViewScript.KOVAC, CoordGrid(3000, 3000)).apply { infoProtocol = NoopNpcInfo }
        val outside = Npc("npc.giants_foundry_kovac_multi_outside", CoordGrid(3360, 3150)).apply { infoProtocol = NoopNpcInfo }
        f.npcs[2] = other
        f.npcs[3] = outside
        f.events.publish(GameLifecycle.Startup)
        assertEquals(FoundryKovacViewScript.UNLOCKED_KOVAC.asRSCM(), f.kovac.visType.id)
        assertEquals(other.type.id, other.visType.id)
        assertEquals(outside.type.id, outside.visType.id)
    }

    private class Fixture {
        val events = EventBus()
        val info = RecordingInfo()
        val kovac = Npc(FoundryKovacViewScript.KOVAC, FoundryKovacViewScript.SPAWN).apply { infoProtocol = info }
        val npcs = NpcList().apply { this[1] = kovac }

        init {
            with(FoundryKovacViewScript(npcs)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }

    private class RecordingInfo : NpcInfoProtocol by NoopNpcInfo {
        val transforms = mutableListOf<Int>()
        val animationTypes = mutableListOf<NpcServerType>()
        override fun setTransmog(type: Int) { transforms += type }
        override fun setBaseAnimations(type: NpcServerType) { animationTypes += type }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
