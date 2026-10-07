package org.rsmod.content.skills.slayer.core

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Npc

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlayerBossExperienceTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `both Queen definitions award the complete two-form experience`() {
        for (name in listOf("npc.kalphite_queen", "npc.kalphite_flyingqueen")) {
            val type = ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))!!
            assertEquals(255, type.hitpoints)
            assertEquals(535.4, SlayerTaskManager.slayerXpForKill(type), 0.00001)
        }
    }

    @Test fun `final Queen kill retains fractional experience after transmog and drained stats`() {
        val grounded = ServerCacheManager.getNpc("npc.kalphite_queen".asRSCM(RSCMType.NPC))!!
        val flying = ServerCacheManager.getNpc("npc.kalphite_flyingqueen".asRSCM(RSCMType.NPC))!!
        val queen = Npc(grounded).apply {
            transmog(flying, Int.MAX_VALUE)
            hitpoints = 0
            attackLvl = 1
            defenceLvl = 1
        }
        assertEquals(535.4, SlayerTaskManager.slayerXpForKill(queen), 0.00001)
    }

    @Test fun `ordinary monsters still use their own health in the experience formula`() {
        val ordinary = NpcServerType(hitpoints = 100, attack = 100, strength = 100, defence = 100)
        assertEquals(100.0, SlayerTaskManager.slayerXpForKill(ordinary), 0.00001)
    }
}
