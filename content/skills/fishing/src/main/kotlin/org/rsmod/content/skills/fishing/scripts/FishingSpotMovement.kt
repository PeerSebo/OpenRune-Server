package org.rsmod.content.skills.fishing.scripts

import dev.openrune.map.MapSingletons.collision
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiContentTimer
import org.rsmod.content.skills.fishing.configs.FishingParams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FishingSpotMovement
@Inject
constructor(
    private val npcList: NpcList,
    private val random: GameRandom,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val spawnPools = mutableMapOf<Int, List<CoordGrid>>()

    private val groupSpawnPools = mutableMapOf<Int, List<CoordGrid>>()

    private val minnowGroupId by lazy { FishingParams.SPOT_MINNOW.asRSCM(RSCMType.CONTENT) }

    override fun ScriptContext.startup() {
        for (group in FishingParams.SPOT_GROUPS) {
            val window = FishingParams.moveWindow(group)
            onAiContentTimer(group) { npc.relocationTimer(window) }
        }
    }

    private fun Npc.relocationTimer(window: IntRange) {
        // The first one-tick timer arms the real relocation window; it must not move the spot immediately.
        val bootstrap = aiTimerStart <= 1
        aiTimer(random.of(window))
        if (!bootstrap) {
            relocate()
        }
    }

    private fun Npc.relocate() {
        if (type.contentGroup == minnowGroupId) {
            relocateMinnow()
            return
        }
        val pool =
            spawnPools.getOrPut(id) {
                npcList.filter { it.id == id }.map { it.spawnCoords }.distinct()
            }
        val destinations = pool.filter { it != coords }
        if (destinations.isEmpty()) {
            return
        }
        val dest = destinations[random.of(destinations.size)]
        telejump(collision, dest)
        worldRepo.soundArea(dest, "synth.FISH_SWIM")
    }

    // Minnow types each have one spawn tile; use their shared platform pool for movement.
    private fun Npc.relocateMinnow() {
        val pool =
            groupSpawnPools.getOrPut(minnowGroupId) {
                npcList
                    .filter { it.type.contentGroup == minnowGroupId }
                    .map { it.spawnCoords }
                    .distinct()
                    .sortedBy { it.packed }
            }
        if (pool.size < 2) {
            return
        }
        val index = pool.indexOf(coords)
        val dest = if (index < 0) pool.first() else pool[(index + 1) % pool.size]
        if (dest == coords) {
            return
        }
        telejump(collision, dest)
        worldRepo.soundArea(dest, "synth.FISH_SWIM")
    }
}
