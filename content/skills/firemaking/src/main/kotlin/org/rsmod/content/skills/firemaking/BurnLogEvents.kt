package org.rsmod.content.skills.firemaking

import dev.openrune.map.MapSingletons.collision
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpObj4
import org.rsmod.api.script.onOpObjU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.FiremakingColoredLogsRow
import org.rsmod.api.table.FiremakingLogsRow
import org.rsmod.content.skills.skillSuccess
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.firstStepDestination
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class BurnLogEvents @Inject constructor(
    private val objRegistry: ObjRegistry,
    private val objRepo: ObjRepository,
    private val locRepo: LocRepository,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) : PluginScript() {

    private val walkDirections = listOf(
        Direction.West,
        Direction.East,
        Direction.South,
        Direction.North,
    )

    private val coloredLogs = FiremakingColoredLogsRow.all().map { it.logItem.internalName }.toSet()

    private val coloredFire: Map<String, String> = FiremakingColoredLogsRow.all()
        .associate { it.logItem.internalName to it.fireObject.internalName }

    override fun ScriptContext.startup() {
        FiremakingLogsRow.all().forEach { log ->
            // Held-item ops run before queue processing; compensate initial scheduling with +1.
            onOpHeldU("obj.tinderbox", log.input) {
                startBurn(log, method = BurnMethod.Tinderbox, affectedEntry = true)
            }
            onOpHeldU(log.input) {
                val barbarianAnim = it.second.paramOrNull(params.barbarian_firemaking_anim)
                    ?: return@onOpHeldU
                val animName = RSCM.getReverseMapping(RSCMType.SEQ, barbarianAnim.id)
                if (animName.isBlank()) {
                    return@onOpHeldU
                }
                startBurn(log, method = BurnMethod.Bow, burnAnim = animName, affectedEntry = true)
            }
            // Ground-object ops run after queue processing; their initial delay needs no +1.
            onOpObj4(log.input) {
                startBurn(log, it.obj, BurnMethod.Tinderbox, affectedEntry = false)
            }
            onOpObjU(log.input, "obj.tinderbox") {
                startBurn(log, it.obj, BurnMethod.Tinderbox, affectedEntry = false)
            }
        }

        onPlayerQueueWithArgs("queue.firemaking_light") { processBurnTick(it.args) }
    }

    private fun ProtectedAccess.burnQueue(
        queue: String,
        cadenceTicks: Int,
        task: BurnTask,
        affectedContext: Boolean,
    ) {
        val n = if (affectedContext) cadenceTicks + 1 else cadenceTicks
        weakQueue(queue, n, task)
    }

    private suspend fun ProtectedAccess.startBurn(
        log: FiremakingLogsRow,
        groundObj: Obj? = null,
        method: BurnMethod,
        burnAnim: String = "seq.human_createfire",
        affectedEntry: Boolean,
    ) {
        // Chain-lighting may begin during the previous fire's forced step.
        arriveDelay()
        if (!canBurn(log, groundObj, method)) {
            resetAnim()
            return
        }

        var obj = groundObj

        if (obj == null) {
            invDel(player.inv, log.input.internalName)
            val duration = player.lootDropDuration ?: constants.lootdrop_duration
            obj = objRepo.add(log.input.internalName, coords, duration, receiver = player)
        }

        stopAction()
        // Sequence 733 contains skeletal audio; do not duplicate its sound.
        anim(burnAnim)
        spam(
            when (method) {
                BurnMethod.Tinderbox -> "You attempt to light the logs."
                BurnMethod.Bow -> "You attempt to light the logs with your bow."
            },
        )

        burnQueue("queue.firemaking_light", 4, BurnTask(log, obj, method, burnAnim), affectedEntry)
    }

    private fun ProtectedAccess.canBurn(
        log: FiremakingLogsRow,
        obj: Obj?,
        method: BurnMethod,
    ): Boolean {
        if (obj != null && !objRegistry.isValid(player, obj)) {
            return false
        }

        if (method == BurnMethod.Tinderbox && !inv.contains("obj.tinderbox")) {
            player.mes("You do not have any fire source to light this.")
            return false
        }

        val reqLevel = when (method) {
            BurnMethod.Tinderbox -> log.statReq.first().t1
            BurnMethod.Bow -> (log.statReq.first().t1 + 20).coerceAtMost(99)
        }
        if (player.firemakingLvl < reqLevel) {
            player.mes("You need a Firemaking level of $reqLevel to burn ${log.input.name} this way.")
            return false
        }

        val fireTile = obj?.coords ?: coords
        if (tileHasCentrepieceLoc(fireTile)) {
            player.mes("You can't light a fire here.")
            return false
        }

        return true
    }

    private fun tileHasCentrepieceLoc(tile: CoordGrid): Boolean =
        locRepo.findExact(tile, LocShape.CentrepieceStraight) != null ||
            locRepo.findExact(tile, LocShape.CentrepieceDiagonal) != null

    private fun ProtectedAccess.processBurnTick(task: BurnTask) {
        if (!canBurn(task.log, task.obj, task.method)) {
            resetAnim()
            return
        }

        val success = coloredLogs.contains(task.log.input.internalName) ||
            skillSuccess(64, 512, player.firemakingLvl)

        if (!success) {
            // Replay the lighting sequence for every retry.
            anim(task.burnAnim)
            burnQueue("queue.firemaking_light", 4, task, affectedContext = true)
            return
        }

        completeBurn(task)
    }

    private fun ProtectedAccess.completeBurn(task: BurnTask) {
        objRepo.del(task.obj)

        val fireCoords = task.obj.coords
        val fireId = coloredFire[task.log.input.internalName] ?: "loc.fire"

        spam("The fire catches and the logs begin to burn.")

        locRepo.add(
            fireCoords,
            fireId,
            random.of(100..200),
            LocAngle.North,
            LocShape.CentrepieceStraight,
            onDespawn = {
                // Use repository lifetime tracking so ashes eventually despawn.
                objRepo.add("obj.ashes", fireCoords, constants.lootdrop_duration)
            },
        )

        soundSynth("synth.FIRE_LIT")

        resetAnim()
        val xpModifier = xpMods.get(player, "stat.firemaking")
        val xp = task.log.xp * task.method.xpMultiplier(task.log.input.internalName) * xpModifier
        statAdvance("stat.firemaking", xp)
        publish(PlayerActionCompleteEvent(player, PlayerAction.BurnLog, task.log.input.internalName, fireCoords))

        moveAwayFromFire(fireCoords)
        faceSquare(fireCoords)
    }

    private fun ProtectedAccess.moveAwayFromFire(coords: CoordGrid) {
        val dest = collision.firstStepDestination(coords, walkDirections) ?: return
        walk(dest)
    }

    data class BurnTask(
        val log: FiremakingLogsRow,
        val obj: Obj,
        val method: BurnMethod,
        val burnAnim: String,
    )

    enum class BurnMethod {
        Tinderbox,
        Bow,
        ;

        fun xpMultiplier(logInternalName: String): Double = 1.0
    }

    private object PyreShipXpMultipliers {
        const val CAMPHOR = 1.361
        const val IRONWOOD = 1.451
        const val ROSEWOOD = 1.567
    }
}
