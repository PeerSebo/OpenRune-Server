package org.rsmod.content.skills.smithing

import org.rsmod.api.player.events.interact.LocUCategoryEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.content.skills.smithing.configs.SmithingParams
import org.rsmod.content.skills.smithing.table.SmithingCannonball
import org.rsmod.content.skills.smithing.util.SmithingUtils
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CannonBallScript : PluginScript() {

    private val allCannonBalls = SmithingCannonball.all()

    override fun ScriptContext.startup() {
        allCannonBalls.forEach { ball ->
            onOpLocCategoryU("category.furnace", ball.input.internalName) {
                if (hasCannonballFurnaceMould()) {
                    startSmelting(it, ball)
                }
            }
        }

        onOpHeldU("obj.mcannonball", "obj.granite_dust") { coatGraniteCannonballs() }

        onPlayerQueueWithArgs<CannonballSmeltTask>(SmithingParams.CANNONBALL_QUEUE) {
            processSmeltTick(it.args)
        }
    }

    private suspend fun ProtectedAccess.startSmelting(
        event: LocUCategoryEvents.Op,
        ball: SmithingCannonball,
    ) {
        startSmelting(event.type.internalName, ball, amount = 28)
    }

    // Op handlers run after queues, so initial scheduling needs no same-tick decrement compensation.
    private suspend fun ProtectedAccess.startSmelting(
        locInternal: String,
        ball: SmithingCannonball,
        amount: Int,
    ) {
        if (!canSmeltCannonballs(ball)) {
            return
        }

        val barsPerSmelt = barsPerSmelt(inv)
        val maxSmelts = minOf(amount, inv.count(ball.input.internalName) / barsPerSmelt)
        if (maxSmelts <= 0) {
            return
        }

        val task =
            CannonballSmeltTask(
                ball = ball,
                locInternal = locInternal,
                targetSmelts = maxSmelts,
                completedSmelts = 0,
            )

        clearWeakQueue(SmithingParams.CANNONBALL_QUEUE)
        scheduleCannonballEffects(task)
        weakQueue(SmithingParams.CANNONBALL_QUEUE, cannonballCadence(locInternal), task)
    }

    private suspend fun ProtectedAccess.processSmeltTick(task: CannonballSmeltTask) {
        if (!canSmeltCannonballs(task.ball)) {
            resetAnim()
            return
        }

        if (!performSmelt(task.ball)) {
            resetAnim()
            return
        }

        val completed = task.completedSmelts + 1
        val moreBars = inv.count(task.ball.input.internalName) >= barsPerSmelt(inv)
        if (completed < task.targetSmelts && moreBars) {
            val next = task.copy(completedSmelts = completed)
            scheduleCannonballEffects(next)
            smithQueue(SmithingParams.CANNONBALL_QUEUE, cannonballCadence(task.locInternal), next)
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.scheduleCannonballEffects(task: CannonballSmeltTask) {
        anim(SmithingParams.FURNACE_ANIM)
        soundSynth(SmithingParams.FURNACE_SOUND)
        val barName = SmithingUtils.itemName(task.ball.input, "bar").lowercase()
        mes("You heat the $barName into a liquid state.")
    }

    private fun ProtectedAccess.performSmelt(ball: SmithingCannonball): Boolean {
        val barsPerSmelt = barsPerSmelt(inv)
        val ballsPerSmelt = ballsPerSmelt(inv)

        val crafted =
            tryProduce(
                inputs = listOf(ball.input.internalName to barsPerSmelt),
                output = ball.output.internalName,
                outputCount = ballsPerSmelt,
            )
        if (!crafted) {
            return false
        }

        statAdvance("stat.smithing", ball.xpPerBar * barsPerSmelt)

        val ballName = SmithingUtils.itemName(ball.output, "cannonball").lowercase()
        mes("You pour the molten metal into your cannonball mould.")
        mes(
            "The molten metal cools slowly to form $ballsPerSmelt " +
                SmithingUtils.pluralize(ballName, ballsPerSmelt) +
                "."
        )
        mes("You remove the cannonballs from the mould.")
        return true
    }

    private suspend fun ProtectedAccess.coatGraniteCannonballs() {
        if (!SmithingUtils.requireSmithingLevel(this, 50, "coat the cannonballs")) {
            return
        }

        val totalBalls = inv.count("obj.mcannonball")
        val graniteDust = inv.count("obj.granite_dust")
        val toCreate = minOf(totalBalls, graniteDust)
        if (toCreate <= 0) {
            return
        }

        val coated =
            tryProduce(
                inputs = listOf("obj.mcannonball" to toCreate, "obj.granite_dust" to toCreate),
                output = "obj.granite_cannonball",
                outputCount = toCreate,
            )
        if (coated) {
            mes("You apply a thick coating of granite dust to your steel cannonballs.")
        }
    }
}

private val cannonballMoulds = arrayOf("obj.ammo_mould", "obj.double_ammo_mould")

internal fun ProtectedAccess.hasCannonballFurnaceMould(): Boolean =
    cannonballMoulds.any(inv::contains)

internal suspend fun ProtectedAccess.openCannonballFurnaceMenu(locInternal: String): Boolean {
    val allCannonBalls = SmithingCannonball.all()
    val available = allCannonBalls.filter { inv.contains(it.input.internalName) }
    if (available.isEmpty()) {
        return false
    }

    val ballsByOutput = allCannonBalls.associateBy { it.output.internalName }
    openSkillMulti(
        SkillMultiConfig(
            verb = "smelt",
            actionType = SkillingActionType.SMELT,
            entries =
                available.map { ball ->
                    SkillMultiEntry(
                        ball.output.internalName,
                        listOf(Material(ball.input.internalName)),
                    )
                },
            maxCountProvider = { inventory, entry ->
                val ball = ballsByOutput[entry.internal] ?: return@SkillMultiConfig 0
                val barsPerSmelt = barsPerSmelt(inventory)
                inventory.count(ball.input.internalName) / barsPerSmelt
            },
        )
    ) { selection ->
        val ball = ballsByOutput[selection.entry.internal] ?: return@openSkillMulti
        startCannonballFurnaceSmelting(locInternal, ball, selection.amount)
    }
    return true
}

private fun barsPerSmelt(inv: org.rsmod.game.inv.Inventory): Int =
    if (inv.contains("obj.double_ammo_mould")) 2 else 1

private fun ballsPerSmelt(inv: org.rsmod.game.inv.Inventory): Int =
    if (inv.contains("obj.double_ammo_mould")) 8 else 4

// TODO: Wire the Grimstone furnace's Sailing requirement.
private fun cannonballCadence(locInternal: String): Int =
    if (locInternal.contains("grimstone_furnace")) {
        SmithingParams.CANNONBALL_TICKS_GRIMSTONE
    } else {
        SmithingParams.CANNONBALL_TICKS
    }

private suspend fun ProtectedAccess.canSmeltCannonballs(ball: SmithingCannonball): Boolean {
    if (!hasCannonballFurnaceMould()) {
        mesbox("You need a mould to do this.")
        return false
    }

    val barsPerSmelt = barsPerSmelt(inv)
    val ballName = ball.output.name
    val barName = ball.input.name

    if (inv.count(ball.input.internalName) < barsPerSmelt) {
        mesbox(
            "You need ${SmithingUtils.countLiteral(barsPerSmelt)} $barName to make " +
                SmithingUtils.prefixAn(ballName) +
                "."
        )
        return false
    }

    return SmithingUtils.requireSmithingLevel(
        this,
        ball.levelReq,
        "smelt ${SmithingUtils.prefixAn(ballName)}",
    )
}

private suspend fun ProtectedAccess.startCannonballFurnaceSmelting(
    locInternal: String,
    ball: SmithingCannonball,
    amount: Int,
) {
    if (!canSmeltCannonballs(ball)) {
        return
    }

    val barsPerSmelt = barsPerSmelt(inv)
    val maxSmelts = minOf(amount, inv.count(ball.input.internalName) / barsPerSmelt)
    if (maxSmelts <= 0) {
        return
    }

    val task =
        CannonballSmeltTask(
            ball = ball,
            locInternal = locInternal,
            targetSmelts = maxSmelts,
            completedSmelts = 0,
        )

    clearWeakQueue(SmithingParams.CANNONBALL_QUEUE)
    anim(SmithingParams.FURNACE_ANIM)
    soundSynth(SmithingParams.FURNACE_SOUND)
    mes("You heat the ${ball.input.name.lowercase()} into a liquid state.")
    smithQueue(SmithingParams.CANNONBALL_QUEUE, cannonballCadence(locInternal), task)
}

private data class CannonballSmeltTask(
    val ball: SmithingCannonball,
    val locInternal: String,
    val targetSmelts: Int,
    val completedSmelts: Int,
)
