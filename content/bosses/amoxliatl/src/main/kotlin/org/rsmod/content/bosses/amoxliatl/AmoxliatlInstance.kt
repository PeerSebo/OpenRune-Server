package org.rsmod.content.bosses.amoxliatl

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class AmoxliatlInstance @Inject constructor(
    registry: BossInstanceRegistry,
    private val deps: BossDeps,
    private val bossHpBar: BossHpBarScript,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val locRepo: LocRepository,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_amoxliatl"

    override fun area(): InstanceArea = INSTANCE

    override fun runsPreludeOnFreshRun(): Boolean = true

    override fun ScriptContext.configure() {
        onEnterPrelude { result, enter ->
            val session =
                when (result) {
                    is InstanceManager.Result.Created -> result.session
                    is InstanceManager.Result.Joined -> result.session
                    else -> return@onEnterPrelude
                }
            val spawnBoss = result.isFreshRun()
            withInstanceEnterTransition(InstanceEnterTransition(), enter)
            if (result is InstanceManager.Result.Created) placeExitBarriers(session)
            if (spawnBoss) {
                val entry = when (result) {
                    is InstanceManager.Result.Created -> result.enter
                    is InstanceManager.Result.Joined -> result.enter
                    else -> return@onEnterPrelude
                }
                deps.worldQueues.addDeferred(SPAWN_REVEAL_DELAY_TICKS) {
                    spawnAmoxliatl(player, session, entry)
                }
            }
        }
        onEnterObject { defaultInstanceEntry() }
        onExitObject { defaultLeaveFlow() }
    }

    private fun placeExitBarriers(session: InstanceSession) {
        for ((template, rotation) in EXIT_BARRIERS) {
            val coords = manager.resolveCoord(session, template) ?: continue
            locRepo.add(coords, EXIT_LOC, Int.MAX_VALUE, LocAngle[rotation], LocShape.CentrepieceStraight)
        }
    }

    private fun spawnAmoxliatl(player: Player, session: InstanceSession, entry: CoordGrid) {
        if (manager.sessionForPlayer(player) !== session || player.hitpoints <= 0) return
        val coords = entry.translate(
            SPAWN_TEMPLATE_COORD.x - ENTRY_TEMPLATE_COORD.x,
            SPAWN_TEMPLATE_COORD.z - ENTRY_TEMPLATE_COORD.z,
        )
        val type = ServerCacheManager.getNpc(AMOXLIATL_NPC_ID) ?: return
        val npc = Npc(type, coords)
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        if (!manager.registerSessionNpc(player, npc)) {
            deps.npcRepo.del(npc, Int.MAX_VALUE)
            return
        }
        npc.anim(SPAWN_SEQ)
        npc.say(SPAWN_SAY)
        deps.suppressAttacks(npc, SPAWN_ATTACK_SUPPRESS_TICKS)
        bossHpBar.onOpen(player, npc)
        npc.apPlayer2(player, aiPlayerInteractions)
    }

    private companion object {
        private val AMOXLIATL_NPC_ID = "npc.amoxliatl".asRSCM(RSCMType.NPC)

        private const val SPAWN_SEQ = "seq.amoxliatl_spawn"
        private const val SPAWN_SAY = "Euat! You are not welcome here!"
        private const val SPAWN_ATTACK_SUPPRESS_TICKS = 1
        private const val SPAWN_REVEAL_DELAY_TICKS = 5

        private val SPAWN_TEMPLATE_COORD = CoordGrid(1362, 4510)
        private val ENTRY_TEMPLATE_COORD = CoordGrid(1371, 4511)

        private const val EXIT_LOC = "loc.amoxliatl_exit"

        private val EXIT_BARRIERS =
            listOf(
                CoordGrid(1377, 4511) to 1,
                CoordGrid(1377, 4510) to 3,
                CoordGrid(1377, 4512) to 3,
            )

        private val INSTANCE = InstanceArea.copyRegions(centerRegionId = 5446)
    }
}
