package org.rsmod.api.game.process.world

import jakarta.inject.Inject
import org.rsmod.api.game.process.npc.NpcPostTickProcess
import org.rsmod.api.game.process.worldentity.WorldEntityMainProcess
import org.rsmod.api.repo.EntityLifecycleProcess

public class WorldPostTickProcess
@Inject
constructor(
    private val worldEntityMain: WorldEntityMainProcess,
    private val npcPostTick: NpcPostTickProcess,
    private val entityLifecycle: EntityLifecycleProcess,
    private val update: WorldUpdateProcess,
) {
    public fun process() {
        // Runs before `PlayerPostTickProcess` publishes `GameLifecycle.UpdateInfo`, which is what
        // triggers `service.infoProtocols.update()`.
        worldEntityMain.process()
        npcPostTick.process()
        entityLifecycle.process()
        update.process()
    }
}
