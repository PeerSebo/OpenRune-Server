package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.death.NpcDeathDropContext
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class DukeRemainsTest {
    @Test fun `Duke suppresses fallback bones before and during his death transmog`() {
        val hook = NoRemainsDeathDropHook()
        val npc = Npc("npc.duke_sucellus_asleep", CoordGrid(3036, 6452))
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val objects = (field.get(null) as Unsafe).allocateInstance(ObjRepository::class.java) as ObjRepository
        val context = NpcDeathDropContext(Player(), npc,
            checkNotNull(ServerCacheManager.getItem("obj.bones".asRSCM())), npc.coords, 100, objects)
        assertTrue(hook.tryConsume(context))
        npc.transmog(checkNotNull(ServerCacheManager.getNpc("npc.duke_sucellus_dead".asRSCM())), Int.MAX_VALUE)
        assertTrue(hook.tryConsume(context))
        assertFalse(hook.tryConsume(context.copy(npc = Npc("npc.goblin", npc.coords))))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
