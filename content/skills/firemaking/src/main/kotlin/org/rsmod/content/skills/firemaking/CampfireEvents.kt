package org.rsmod.content.skills.firemaking

import dev.openrune.ServerCacheManager
import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.controller.vars.intVarCon
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.loc.locNearby
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiConTimer
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.FiremakingColoredLogsRow
import org.rsmod.api.table.FiremakingLogsRow
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Controller
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CampfireEvents @Inject constructor(
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val conRepo: ControllerRepository,
    private val mapClock: MapClock,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) : PluginScript() {

    private val coloredLogRows = FiremakingColoredLogsRow.all()

    private val campFireObjects =
        coloredLogRows.map { it.campfireObject.internalName }.toMutableSet().apply { add("loc.forestry_fire") }

    private val basicFires = coloredLogRows.map { it.fireObject.internalName }.toSet() + "loc.fire"

    private val coloredLogNames = coloredLogRows.map { it.logItem.internalName }.toSet()

    private val burnableLogs = FiremakingLogsRow.all().filterNot { it.input.internalName in coloredLogNames }

    // Campfire colour comes from the existing fire, not the converting log.
    private val campfireLocForFire =
        coloredLogRows.associate { it.fireObject.internalName to it.campfireObject.internalName }

    override fun ScriptContext.startup() {
        onPlayerQueueWithArgs("queue.firemaking_campfire_tend") { processCampfireTendTick(it.args) }
        onPlayerQueueWithArgs("queue.firemaking_campfire_rest") { processCampfireRestTick(it.args) }

        onAiConTimer("controller.firemaking_campfire") { controller.campfireSmokeTick() }

        campFireObjects.forEach { camp ->
            onOpLoc1(camp) { tendCampfireMenu(it.vis) }
            onOpLoc2(camp) { showCampfireStatus(it.vis) }
            onOpLoc3(camp) { restByCampfire() }

            burnableLogs.forEach { log ->
                onOpLocU(camp, log.input.internalName) {
                    tendCampfireWithLog(it.vis, log)
                }
            }
        }

        basicFires.forEach { fire ->
            burnableLogs.forEach { log ->
                onOpLocU(fire, log.input.internalName) {
                    lightCampfire(it, log)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.lightCampfire(event: LocUEvents.Op, log: FiremakingLogsRow) {
        if (!canLight(log)) return

        val fire = event.vis
        val tile = fire.coords

        if (locRepo.locNearby(tile, 5, tile, campFireObjects)) {
            player.mes("There's a Forester's Campfire nearby, help tend to that one or move further away.")
            return
        }

        val fireObject = campfireLocForFire[fire.internalName] ?: "loc.forestry_fire"

        // Arrival already supplies the approach delay; do not add another script pause.
        arriveDelay()
        noLoopForesterAnim(log)?.let(::anim)

        delay(COMPLETION_TICKS)

        // Revalidate logs and fire after the interruptible wind-up.
        if (inv.count(log.input.internalName) <= 0) return
        if (!locRepo.findLoc(tile, fire.internalName)) return

        spam("You finish adding the ${log.input.name} to the fire.")

        locRepo.del(fire, Int.MAX_VALUE)
        spawnCampfire(fire, fireObject, log.foresterInitialTicks.coerceAtLeast(1))
        emitCampfireSmoke(tile)

        invDel(player.inv, log.input.internalName, 1)

        // Map animation is zone-wide here; native presentation is player-specific.
        spotanimMap(worldRepo, "spotanim.forestry_campfire_burning_spotanim", tile)
        soundSynth("synth.FIRE_LIT")

        val xpModifier = xpMods.get(player, "stat.firemaking")
        statAdvance("stat.firemaking", log.xp * xpModifier)
    }

    private suspend fun ProtectedAccess.tendCampfireMenu(camp: BoundLocInfo) {
        if (logsCarriedForTending().isEmpty()) {
            mesbox(NOTHING_TO_BURN)
            return
        }

        val log = bestAvailableLog() ?: return
        tendCampfireWithLog(camp, log)
    }

    private fun ProtectedAccess.tendCampfireWithLog(camp: BoundLocInfo, log: FiremakingLogsRow) {
        if (!canTend(log, camp)) {
            resetAnim()
            stopAction()
            return
        }
        weakQueue("queue.firemaking_campfire_tend", 1, CampfireTendTask(camp, log))
    }

    private fun ProtectedAccess.processCampfireTendTick(task: CampfireTendTask) {
        val camp = resolveCampfire(task.campfire) ?: return
        if (!canTend(task.log, task.campfire)) {
            resetAnim()
            stopAction()
            return
        }

        task.log.foresterAnimation?.internalName?.let(::anim)

        extendCampfire(camp, task.log.foresterLogTicks)

        val xpModifier = xpMods.get(player, "stat.firemaking")
        val xp = task.log.xp * xpModifier
        statAdvance("stat.firemaking", xp)
        invDel(player.inv, task.log.input.internalName, 1)

        spam("You finish adding the ${task.log.input.name} to the fire.")
        soundSynth("synth.FIRE_LIT")

        spotanimMap(worldRepo, "spotanim.forestry_campfire_burning_spotanim", camp.coords)

        weakQueue("queue.firemaking_campfire_tend", 9, task)
    }

    private suspend fun ProtectedAccess.showCampfireStatus(camp: BoundLocInfo) {
        resetAnim()

        val text = when (campfireRemainingTicks(camp)) {
            in 0..58 -> "The embers glow softly."
            in 59..118 -> "The flames flicker gently."
            in 119..178 -> "The fire burns steadily."
            in 179..238 -> "The fire burns brightly."
            else -> "The roaring fire crackles invitingly."
        }

        mesbox(text)
    }

    private fun ProtectedAccess.restByCampfire() {
        mes("You rest by the welcoming fire.")
        anim("seq.forestry_sitting_tea")
        weakQueue("queue.firemaking_campfire_rest", REST_LOOP_TICKS, CampfireRestTask(coords))
    }

    private fun ProtectedAccess.processCampfireRestTick(task: CampfireRestTask) {
        // Revalidate rest state after suspension so movement can interrupt it.
        if (coords != task.coords) return
        anim("seq.forestry_sitting_tea_loop")
    }

    private fun ProtectedAccess.canLight(log: FiremakingLogsRow): Boolean =
        hasTinderboxOrMes() && hasFiremakingLevelOrMes(log)

    private fun ProtectedAccess.canTend(log: FiremakingLogsRow, camp: BoundLocInfo): Boolean {
        if (inv.count(log.input.internalName) <= 0) return false
        if (!locRepo.findLoc(camp.coords, camp.internalName)) return false
        return hasFiremakingLevelOrMes(log)
    }

    private fun ProtectedAccess.hasTinderboxOrMes(): Boolean {
        if (inv.contains("obj.tinderbox")) return true
        player.mes("You need a Tinderbox to do this.")
        return false
    }

    private fun ProtectedAccess.hasFiremakingLevelOrMes(log: FiremakingLogsRow): Boolean {
        if (player.firemakingLvl >= log.statReq.first().t1) return true
        player.mes("You need a Firemaking level of ${log.statReq.first().t1} to burn ${log.input.name}.")
        return false
    }

    private suspend fun ProtectedAccess.bestAvailableLog(): FiremakingLogsRow? {
        val logs = logsCarriedForTending()

        when (logs.size) {
            0 -> return null
            1 -> return logs.first()
        }

        var chosen: FiremakingLogsRow? = null

        openSkillMulti(SkillMultiConfig(verb = "burn", entries = logs.map {
          SkillMultiEntry(it.input.internalName)
        }),) { selection ->
            chosen = logs.firstOrNull { it.input.internalName == selection.entry.internal }
        }

        return chosen
    }

    private fun ProtectedAccess.logsCarriedForTending(): List<FiremakingLogsRow> =
        burnableLogs.filter {
            player.firemakingLvl >= it.statReq.first().t1 && inv.count(it.input.internalName) > 0
        }

    // The table stores looping sequences; select the _noloop twin for a single tend.
    private fun noLoopForesterAnim(log: FiremakingLogsRow): String? {
        val looping = log.foresterAnimation?.internalName ?: return null
        val noLoop = "${looping}_noloop"
        return if (ConstantProvider.getMappingOrNull(noLoop) != null) noLoop else looping
    }

    private fun spawnCampfire(template: BoundLocInfo, id: String, duration: Int) {
        val coords = template.coords

        ensureController(coords).apply {
            firemakingCampfireExpiryCycle = mapClock + duration
            aiTimer(random.of(SMOKE_MIN_TICKS, SMOKE_MAX_TICKS))
        }

        locRepo.add(
            coords,
            id,
            duration,
            template.angle,
            template.shape,
            onDespawn = {
                conRepo.findExact(coords, "controller.firemaking_campfire")?.let(conRepo::del)
                // Registry-only drops lack a lifetime; use objRepo for ashes.
                objRepo.add("obj.ashes", coords, constants.lootdrop_duration)
            }
        )
    }

    private fun emitCampfireSmoke(coords: CoordGrid) {
        val spotanim = random.pick(SMOKE_SPOTANIMS).asRSCM(RSCMType.SPOTANIM)
        worldRepo.spotanimMap(SpotanimType(spotanim), coords)
    }

    private fun Controller.campfireSmokeTick() {
        // The controller can outlive its loc by one tick; test loc existence before emitting.
        val burning = campFireObjects.any { locRepo.findLoc(coords, it) }
        if (!burning) {
            conRepo.del(this)
            return
        }

        emitCampfireSmoke(coords)
        aiTimer(random.of(SMOKE_MIN_TICKS, SMOKE_MAX_TICKS))
    }

    private fun extendCampfire(camp: BoundLocInfo, add: Int) {
        val newDuration = (campfireRemainingTicks(camp) + add).coerceIn(1, 300)
        locRepo.del(camp, Int.MAX_VALUE)
        spawnCampfire(camp, camp.internalName, newDuration)
    }

    private fun campfireRemainingTicks(camp: BoundLocInfo): Int {
        val expiry = conRepo.findExact(camp.coords, "controller.firemaking_campfire")
            ?.firemakingCampfireExpiryCycle ?: return 300

        return (expiry - mapClock.cycle).coerceAtLeast(0)
    }

    private fun ensureController(coords: CoordGrid): Controller =
        conRepo.findExact(coords, "controller.firemaking_campfire")
            ?: Controller("controller.firemaking_campfire", coords).also {
                conRepo.add(it, 5000)
                it.firemakingCampfireExpiryCycle = 0
            }

    private fun resolveCampfire(camp: BoundLocInfo): BoundLocInfo? {
        val type = ServerCacheManager
            .getObject(camp.internalName.asRSCM(RSCMType.LOC))
            ?: return null

        return locRepo.findExact(camp.coords, type)?.let { BoundLocInfo(it, type) }
    }

    private data class CampfireTendTask(
        val campfire: BoundLocInfo,
        val log: FiremakingLogsRow
    )

    private data class CampfireRestTask(val coords: CoordGrid)

    companion object {
        var Controller.firemakingCampfireExpiryCycle: Int by intVarCon("varcon.firemaking_campfire_expiry_cycle")

        private const val COMPLETION_TICKS = 4

        private const val REST_LOOP_TICKS = 13

        private const val SMOKE_MIN_TICKS = 2
        private const val SMOKE_MAX_TICKS = 3

        private val SMOKE_SPOTANIMS =
            listOf(
                "spotanim.forestry_campfire_smoke_01",
                "spotanim.forestry_campfire_smoke_02",
                "spotanim.forestry_campfire_smoke_03",
                "spotanim.forestry_campfire_smoke_04",
            )

        private const val NOTHING_TO_BURN = "You don't have anything you can burn on this fire."
    }
}
