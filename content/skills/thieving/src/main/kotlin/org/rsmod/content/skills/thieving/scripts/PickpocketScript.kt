package org.rsmod.content.skills.thieving.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.droptable.looksLikeClueScrollObj
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.mechanics.stun.isStunActionBlocked
import org.rsmod.api.mechanics.stun.stun
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.hit.playDefendSound
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onNpcQueueWithArgs
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.script.onPlayerSoftQueueWithArgs
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.CoinPouches
import org.rsmod.content.skills.thieving.configs.PickpocketNpc
import org.rsmod.content.skills.thieving.configs.ThievingNpcs
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.content.skills.thieving.pets.RockyPetManager
import org.rsmod.content.skills.thieving.pets.RockyPetRates
import org.rsmod.content.skills.thieving.scripts.ThievingGear.applyBonuses
import org.rsmod.content.skills.thieving.scripts.ThievingGear.applyTzHaarSuccessDamage
import org.rsmod.content.skills.thieving.scripts.ThievingGear.coinPouchCap
import org.rsmod.content.skills.thieving.scripts.ThievingGear.coinPouchCount
import org.rsmod.content.skills.thieving.scripts.ThievingGear.degradeGlovesOfSilence
import org.rsmod.content.skills.thieving.scripts.ThievingGear.negatesFailure
import org.rsmod.content.skills.thieving.scripts.ThievingGear.rollRogueDoubleLoot
import org.rsmod.content.skills.thieving.scripts.ThievingLoot.ThievingDrop
import org.rsmod.content.skills.thieving.scripts.ThievingLoot.giveDrop
import org.rsmod.content.skills.thieving.scripts.ThievingLoot.rollLoot
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.getOrNull
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PickpocketScript
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val rocky: RockyPetManager,
    private val access: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (target in ThievingNpcs.all) {
            for (npc in target.npcs) {
                when (target.pickpocketOp) {
                    1 -> onOpNpc1(npc) { pickpocket(it.npc, target) }
                    2 -> onOpNpc2(npc) { pickpocket(it.npc, target) }
                    else -> onOpNpc3(npc) { pickpocket(it.npc, target) }
                }
            }
        }

        onPlayerSoftQueueWithArgs<SuccessTask>(ThievingRates.PICKPOCKET_SUCCESS_QUEUE) {
            access.launchNonInterrupting(player) { resolveSuccess(args) }
        }
        onPlayerQueueWithArgs<FailTask>(ThievingRates.PICKPOCKET_FAIL_REACT_QUEUE) { event ->
            failReact(event.args)
        }
        onPlayerQueueWithArgs<FailTask>(ThievingRates.PICKPOCKET_FAIL_STRIKE_QUEUE) { event ->
            failStrike(event.args)
        }
        onPlayerQueueWithArgs<FailTask>(ThievingRates.PICKPOCKET_FAIL_STUN_QUEUE) { event ->
            failStun(event.args)
        }

        onNpcQueueWithArgs<FaceLock>(ThievingRates.PICKPOCKET_RELEASE_FACE_QUEUE) { event ->
            val lock = event.args
            if (npc.coords != lock.origin || lock.remaining <= 1) {
                npc.resetFaceEntity()
                return@onNpcQueueWithArgs
            }
            npc.queue(
                ThievingRates.PICKPOCKET_RELEASE_FACE_QUEUE,
                cycles = 1,
                args = lock.copy(remaining = lock.remaining - 1),
            )
        }
    }

    private data class FaceLock(val origin: CoordGrid, val remaining: Int)

    private data class FailTask(val npcUid: NpcUid, val target: PickpocketNpc)

    private data class SuccessTask(
        val npcUid: NpcUid,
        val target: PickpocketNpc,
        val guaranteed: Boolean,
        val npcSymbol: String,
        val coords: CoordGrid,
    )

    private suspend fun ProtectedAccess.pickpocket(npc: Npc, target: PickpocketNpc) {
        // Suspend until the first legal tick; returning would consume the buffered interaction.
        val cooldownRemaining = actionDelay - mapClock
        if (cooldownRemaining > 0) {
            delay(cooldownRemaining)
        }

        arriveDelay()

        while (actionDelay > mapClock) {
            delay(actionDelay - mapClock)
        }

        // The movement gate already reports stun; a second message here would duplicate it.
        if (isStunActionBlocked) {
            return
        }

        if (isInCombat()) {
            mes("You can't pickpocket while you're in combat.")
            return
        }

        if (npc.isInCombat()) {
            mes("You can't pickpocket the ${target.displayName} while they're in combat.")
            return
        }

        if (player.thievingLvl < target.levelReq) {
            mes(
                "You need a Thieving level of ${target.levelReq} to pick " +
                    "the ${target.displayName}'s pocket."
            )
            return
        }

        // Guaranteed attempts pay coins directly and need neither pouch capacity nor a free slot.
        val guaranteed = BlackjackState.consumeGuaranteedPickpocket(player, npc)

        if (!guaranteed) {
            if (target.usesCoinPouches && coinPouchCount() >= coinPouchCap()) {
                mes(
                    "You have too many coin pouches. You should open some before " +
                        "pickpocketing again."
                )
                soundSynth("synth.PILLORY_WRONG")
                return
            }

            if (inv.isFull()) {
                mes("Your inventory is too full to hold any more items.")
                soundSynth("synth.PILLORY_WRONG")
                return
            }
        }

        // Commit cooldown only after checks pass, so rejected attempts do not spend it.
        actionDelay = mapClock + target.actionDelay

        faceEntitySquare(npc)
        spam("You attempt to pick the ${target.displayName}'s pocket.")

        if (guaranteed || rollPickpocket(target)) {
            player.softQueue(
                ThievingRates.PICKPOCKET_SUCCESS_QUEUE,
                cycles = 1,
                args = SuccessTask(npc.uid, target, guaranteed, RSCM.getReverseMapping(RSCMType.NPC, npc.type.id), npc.coords),
                startNextCycle = true,
                closeModals = false,
            )
            return
        }

        // TODO: Verify the timing of dodgy-necklace and Shadow Veil reactions.
        if (negatesFailure()) {
            return
        }

        queue(ThievingRates.PICKPOCKET_FAIL_REACT_QUEUE, cycles = 1, args = FailTask(npc.uid, target))
        queue(ThievingRates.PICKPOCKET_FAIL_STRIKE_QUEUE, cycles = 2, args = FailTask(npc.uid, target))
        queue(ThievingRates.PICKPOCKET_FAIL_STUN_QUEUE, cycles = 3, args = FailTask(npc.uid, target))
    }

    private fun ProtectedAccess.rollPickpocket(target: PickpocketNpc): Boolean {
        val low = applyBonuses(target.low, target)
        val high = applyBonuses(target.high, target)
        return statRandom("stat.thieving", low, high, invisibleLvls)
    }

    private fun ProtectedAccess.resolveSuccess(task: SuccessTask) {
        anim(
            if (task.guaranteed) {
                ThievingRates.PICKPOCKET_KNOCKOUT_ANIM
            } else {
                ThievingRates.PICKPOCKET_ANIM
            }
        )
        soundSynth(ThievingRates.PICKPOCKET_SUCCESS_SOUND)
        spam("You pick the ${task.target.displayName}'s pocket.")
        awardPickpocket(task.target, task.guaranteed)
        publish(PlayerActionCompleteEvent(player, PlayerAction.Pickpocket, task.npcSymbol, task.coords))
    }

    private fun ProtectedAccess.awardPickpocket(target: PickpocketNpc, guaranteed: Boolean) {
        val doubleLoot = rollRogueDoubleLoot()

        val drops = mutableListOf<ThievingDrop>()
        target.pouch?.let { pouch -> drops += ThievingDrop(pouch, 1) }
        target.loot(player)?.let { table -> drops += rollLoot(table) }

        for (drop in drops) {
            if (guaranteed && CoinPouches.isCoinPouch(drop.obj)) {
                payPouchValueDirect(drop)
                if (doubleLoot) {
                    payPouchValueDirect(drop)
                }
            } else {
                giveDrop(objRepo, drop)
                if (doubleLoot) {
                    grantRogueBonus(drop)
                }
            }
        }

        target.directCoins?.let { coins -> invAddOrDrop(objRepo, "obj.coins", if (doubleLoot) coins * 2 else coins) }

        statAdvance("stat.thieving", target.xp * xpMods.get(player, "stat.thieving"))
        rocky.roll(this, RockyPetRates.pickpocket(target))

        if (target.prifddinas) {
            for (drop in rollPrifddinasLoot(doubleLoot) { random.of(it) }) {
                giveDrop(objRepo, drop)
                if (drop.obj == "obj.prif_crystal_shard") spam("You find a crystal shard.")
                else mes("You find an enhanced crystal teleport seed!")
            }
        }

        applyTzHaarSuccessDamage(target)
    }

    // Double loot awards one pouch plus its value in coins; never double clue boxes or XP.
    private fun ProtectedAccess.grantRogueBonus(drop: ThievingDrop) {
        if (looksLikeClueScrollObj(drop.obj)) {
            return
        }

        if (CoinPouches.isCoinPouch(drop.obj)) {
            payPouchValueDirect(drop)
            return
        }

        giveDrop(objRepo, drop)
    }

    private fun ProtectedAccess.payPouchValueDirect(drop: ThievingDrop) {
        val pouchValue = CoinPouches.coinsPerPouch[drop.obj] ?: return
        val coins = (0 until drop.count).sumOf { random.of(pouchValue) }
        if (coins > 0) {
            invAddOrDrop(objRepo, "obj.coins", coins)
        }
    }

    private fun ProtectedAccess.failReact(task: FailTask) {
        spam("You fail to pick the ${task.target.displayName}'s pocket.")
        findUid(task.npcUid)?.takeIf { it.isValidTarget() }?.say(task.target.failBark)
    }

    // Apply the stun lock with the strike, one tick before the message and damage.
    private fun ProtectedAccess.failStrike(task: FailTask) {
        val target = task.target

        anim(failureBlockAnim())

        stun(
            movementCycles = target.stunMovement,
            actionCycles = target.stunAction,
            spotanim = ThievingRates.PICKPOCKET_STUN_SPOTANIM,
            spotanimHeight = Stun.DEFAULT_SPOTANIM_HEIGHT,
        )

        findUid(task.npcUid)?.takeIf { it.isValidTarget() }?.let { npc ->
            npc.facePlayer(player)
            npc.anim(failureAttackAnim(npc))
        }

        soundSynth(ThievingRates.PICKPOCKET_STUN_SOUND)
    }

    private fun ProtectedAccess.failStun(task: FailTask) {
        val target = task.target

        // TODO: Add H.A.M. ejection/jail after repeated failures, reduced by worn H.A.M. pieces.
        // Use instant damage here; queued impact would add another tick.
        val damage =
            if (target.alwaysDamagesWithoutIceGloves) {
                ThievingRates.TZHAAR_HUR_DAMAGE
            } else {
                random.of(1, target.damage)
            }
        takeInstantHit(type = HitType.Typeless, damage = damage)

        // Instant hits bypass StandardPlayerHitProcessor; play their defend sound explicitly.
        playDefendSound(damage)

        spam("You've been stunned!")

        degradeGlovesOfSilence()

        findUid(task.npcUid)?.takeIf { it.isValidTarget() }?.let { npc ->
            npc.queue(
                ThievingRates.PICKPOCKET_RELEASE_FACE_QUEUE,
                cycles = 1,
                args = FaceLock(npc.coords, ThievingRates.PICKPOCKET_FOCUS_CYCLES),
            )
        }

        // Failed pickpockets do not start combat. Stop the action on the damage/message tick.
        stopAction()
    }

    private fun ProtectedAccess.failureAttackAnim(npc: Npc): String {
        val seq =
            npcVisType(npc).paramOrNull(params.attack_anim)
                ?: return ThievingRates.PICKPOCKET_FALLBACK_ATTACK_ANIM
        return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
    }

    private fun ProtectedAccess.failureBlockAnim(): String {
        val seq =
            getOrNull(player.righthand)?.paramOrNull(params.defend_anim)
                ?: return ThievingRates.PICKPOCKET_BLOCK_ANIM
        return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
    }
}
