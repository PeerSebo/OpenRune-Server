package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.HunterCatchEvent
import org.rsmod.content.skills.hunter.configs.HunterCatchRates
import org.rsmod.content.skills.hunter.configs.HunterMessages
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.content.skills.hunter.configs.NetPrey
import org.rsmod.content.skills.hunter.configs.NetPreyKind
import org.rsmod.content.skills.hunter.configs.NetPreyTable
import org.rsmod.content.skills.hunter.configs.NettingParams
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal enum class NetCatchMethod { NET, MAGIC_NET, BAREHANDED }
internal data class NetCatchTask(val target: NpcUid, val origin: CoordGrid, val npcType: Int, val method: NetCatchMethod)

class NetCatchScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val eventBus: EventBus,
) : PluginScript() {
    private val preyByNpcId: Map<Int, NetPrey> by lazy {
        buildMap {
            for (prey in NetPreyTable.ALL) {
                for (npc in prey.npcs) {
                    put(npc.asRSCM(RSCMType.NPC), prey)
                }
            }
        }
    }

    private val netObjIds: Set<Int> by lazy {
        NettingParams.NETS.mapTo(mutableSetOf()) { it.asRSCM(RSCMType.OBJ) }
    }

    override fun ScriptContext.startup() {
        for (prey in NetPreyTable.ALL) {
            for (npc in prey.npcs) {
                onOpNpc1(npc) { startCatch(it.npc) }
                onOpNpcU(npc) { useOnPrey(it.npc, it.objType) }
            }
        }
        onPlayerQueueWithArgs<NetCatchTask>(NettingParams.CATCH_QUEUE) { finishCatch(it.args) }
        for (prey in NetPreyTable.ALL.filter { it.kind == NetPreyKind.BUTTERFLY }) {
            onOpHeld4(prey.filledJar) { releaseJar(prey, it.slot) }
        }
    }

    private fun ProtectedAccess.useOnPrey(npc: Npc, objType: ItemServerType) {
        if (objType.id !in netObjIds) {
            mes(constants.dm_default)
            return
        }
        startCatch(npc)
    }

    internal fun ProtectedAccess.startCatch(npc: Npc) {
        val prey = preyByNpcId[npc.visType.id] ?: return
        clearQueue(NettingParams.CATCH_QUEUE)
        val method = catchMethod()
        if (!requirements(npc, prey, method)) { resetAnim(); return }

        faceEntitySquare(npc)
        anim(if (method == NetCatchMethod.BAREHANDED) NettingParams.IMPLING_ANIM else NettingParams.anim(prey.kind))
        if (method != NetCatchMethod.BAREHANDED) soundSynth(NettingParams.SWING_SYNTH)

        // NPC ops run after queue processing; their initial delay needs no +1.
        queue(NettingParams.CATCH_QUEUE, NettingParams.catchTicks(prey.kind), NetCatchTask(npc.uid, coords, npc.visType.id, method))
    }

    internal fun ProtectedAccess.finishCatch(task: NetCatchTask) {
        // Catch sequences loop beyond the action; reset them on completion.
        resetAnim()

        // despawn leaves the NPC registered but hidden; isValidTarget prevents catching it twice.
        val npc = findUid(task.target)
        if (npc == null || !npc.isValidTarget() || npc.visType.id != task.npcType || coords != task.origin || !isWithinDistance(npc, 1)) {
            mes(HunterMessages.NET_TARGET_GONE)
            return
        }
        val prey = preyByNpcId[npc.visType.id] ?: return
        if (catchMethod() != task.method || !requirements(npc, prey, task.method)) return

        val enhanced = task.method == NetCatchMethod.MAGIC_NET || (task.method == NetCatchMethod.BAREHANDED && prey.barehandedBonus)
        val rate = if (enhanced) prey.enhancedRate else prey.rate
        val caught =
            rate == null ||
                random.of(HunterCatchRates.DENOMINATOR) <
                    org.rsmod.content.skills.hunter.HunterGuildOutfit.catchChance(player, HunterCatchRates.chance(rate, player.hunterLvl))
        if (!caught) {
            mes(HunterMessages.NET_CATCH_FAILED)
            return
        }

        val jar = NettingParams.emptyJar(prey.kind)
        val jarred = inv.count(jar) > 0
        if (jarred && !invReplace(inv, jar, count = 1, replacement = prey.filledJar).success) {
            mes(missingJarMessage(prey))
            return
        }
        if (!jarred) {
            if (prey.kind == NetPreyKind.IMPLING) {
                val rewards = ImplingLoot.roll(prey.filledJar.substringAfterLast('_').toInt(), random)
                with(ImplingLoot) { award(objRepo, rewards) }
            } else releaseButterfly(prey)
        }
        if (prey.filledJar == "obj.butterfly_jar_sunmoth") {
            player.runEnergy = (player.runEnergy + constants.run_max_energy / 20).coerceAtMost(constants.run_max_energy)
            UpdateRun.energy(player, player.runEnergy)
        }
        statAdvance(
            HunterParams.HUNTER_STAT,
            prey.xp * xpMods.get(player, HunterParams.HUNTER_STAT),
        )
        val message =
            if (prey.kind == NetPreyKind.IMPLING) {
                if (jarred) HunterMessages.IMPLING_CAUGHT else "You catch the impling and take its loot."
            } else {
                HunterMessages.caught(npc.visType.name)
            }
        spam(message)

        // The half-hour scheduler owns crystal impling respawn.
        npcRepo.despawn(npc, if (prey.filledJar == "obj.ii_captured_impling_12") Int.MAX_VALUE else NettingParams.respawnTicks(prey.kind))
        eventBus.publish(HunterCatchEvent(player, prey.npcs.first()))
        publish(PlayerActionCompleteEvent(
            player, PlayerAction.CatchCreature,
            npc.visType.internalName, npc.coords,
        ))
        publish(PlayerActionCompleteEvent(
            player, PlayerAction.CatchCreature,
            npc.visType.internalName, npc.coords,
        ))
    }

    internal fun ProtectedAccess.catchMethod(): NetCatchMethod = when {
        worn.count(NettingParams.MAGIC_NET) > 0 -> NetCatchMethod.MAGIC_NET
        worn.count(NettingParams.BUTTERFLY_NET) > 0 -> NetCatchMethod.NET
        else -> NetCatchMethod.BAREHANDED
    }

    private fun ProtectedAccess.requirements(npc: Npc, prey: NetPrey, method: NetCatchMethod): Boolean {
        if (prey.filledJar == "obj.ii_captured_impling_12" && !QuestRequirements.hasCompleted(player, "quest_songoftheelves")) {
            mes("You need to complete Song of the Elves before catching crystal implings.")
            return false
        }
        val required = prey.levelReq + if (method == NetCatchMethod.BAREHANDED) 10 else 0
        if (player.hunterLvl < required) {
            mes(HunterMessages.levelRequirement(required, catchAction(npc) + if (method == NetCatchMethod.BAREHANDED) " barehanded" else ""))
            return false
        }
        if (prey.needsJar && inv.count(NettingParams.IMPLING_JAR) == 0) {
            mes(HunterMessages.NET_NO_IMPLING_JAR)
            return false
        }
        return npc.isValidTarget() && isWithinDistance(npc, 1)
    }

    private fun ProtectedAccess.releaseButterfly(prey: NetPrey) {
        ButterflyEffects.apply(player, prey)
    }

    internal fun ProtectedAccess.releaseJar(prey: NetPrey, slot: Int) {
        if (prey.kind != NetPreyKind.BUTTERFLY || !inv[slot].isType(prey.filledJar)) return
        val empty = checkNotNull(ServerCacheManager.getItem(NettingParams.BUTTERFLY_JAR.asRSCM()))
        if (!invReplaceSlot(inv, slot, 1, empty).success) return
        releaseButterfly(prey)
        mes("You release the creature from the jar.")
    }

    private fun missingJarMessage(prey: NetPrey): String =
        when (prey.kind) {
            NetPreyKind.BUTTERFLY -> HunterMessages.NET_NO_BUTTERFLY_JAR
            NetPreyKind.IMPLING -> HunterMessages.NET_NO_IMPLING_JAR
        }

    private fun catchAction(npc: Npc): String = "catch a ${npc.visType.name.lowercase()}"
}
