package org.rsmod.content.areas.misc.goblinvillage

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.isOutOfCombat
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onAiTimer
import org.rsmod.content.areas.misc.goblinvillage.configs.GoblinInfightParams
import org.rsmod.content.areas.misc.goblinvillage.configs.GoblinSoldier
import org.rsmod.content.areas.misc.goblinvillage.configs.GoblinSoldiers
import org.rsmod.content.areas.misc.goblinvillage.configs.GoblinTaunts
import org.rsmod.content.areas.misc.goblinvillage.configs.pick
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GoblinInfight
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val rayCast: RayCastValidator,
    private val hitModifier: NpcHitModifier,
    private val random: GameRandom,
) : PluginScript() {
    private val profiles = HashMap<Int, GoblinSoldier>()

    private val bouts = HashMap<NpcUid, Bout>()

    private val reengageAt = HashMap<NpcUid, Int>()

    override fun ScriptContext.startup() {
        for (soldier in GoblinSoldiers.All) {
            profiles[soldier.npc.asRSCM(RSCMType.NPC)] = soldier
            onAiTimer(soldier.npc) { npc.infightTick() }
        }
    }

    private fun Npc.infightTick() {
        // Reschedule before early returns so the AI timer cannot retire permanently.
        aiTimer(BeatInterval)

        val bout = bouts[uid]
        if (bout != null) {
            // Only the leader drives; the follower's own tick just checks the bout is still real,
            // so a leader that despawns or dies cannot leave it frozen in `NpcMode.None` forever.
            if (bout.leaderUid == uid) {
                bout.drive()
            } else if (!bout.stillValid()) {
                bout.end()
            }
            return
        }

        tryEngage()
    }

    private fun Npc.tryEngage() {
        val profile = profiles[id] ?: return
        if (!canBicker()) {
            return
        }

        for (neighbour in CardinalOffsets) {
            val coord = coords.translate(neighbour.first, neighbour.second)
            val target =
                npcRepo.findAll(coord).firstOrNull { other ->
                    profiles[other.id]?.side == profile.side.opposite &&
                        other.uid !in bouts &&
                        other.canBicker() &&
                        canReach(other)
                } ?: continue

            val targetProfile = profiles[target.id] ?: continue
            startBout(this, profile, target, targetProfile)
            return
        }
    }

    private fun Npc.canBicker(): Boolean {
        if (!isValidTarget() || isBusy || !isOutOfCombat()) {
            return false
        }
        val cooldown = reengageAt[uid] ?: return true
        if (currentMapClock < cooldown) {
            return false
        }
        // Prune as soon as it lapses; a goblin that dies is reassigned a fresh uid on respawn, so
        // entries left behind here would never be looked up again and would only accumulate.
        reengageAt.remove(uid)
        return true
    }

    private fun Npc.canReach(other: Npc): Boolean {
        if (coords.level != other.coords.level) {
            return false
        }
        return rayCast.hasLineOfWalk(coords, other.coords)
    }

    private fun startBout(
        first: Npc,
        firstProfile: GoblinSoldier,
        second: Npc,
        secondProfile: GoblinSoldier,
    ) {
        // Use the lower NPC slot as leader: AI timers and queues process in ascending slot order.
        val leaderIsFirst = first.slotId <= second.slotId
        val leader = if (leaderIsFirst) first else second
        val leaderProfile = if (leaderIsFirst) firstProfile else secondProfile
        val follower = if (leaderIsFirst) second else first
        val followerProfile = if (leaderIsFirst) secondProfile else firstProfile

        val bout = Bout(leader, leaderProfile, follower, followerProfile)
        bouts[bout.leaderUid] = bout
        bouts[bout.followerUid] = bout

        // `noneMode` pins them and clears any existing entity facing, so face *after* it.
        leader.noneMode()
        follower.noneMode()
        leader.faceNpc(follower)
        follower.faceNpc(leader)
    }

    private fun Bout.drive() {
        if (!stillValid()) {
            end()
            return
        }

        when (tick % GoblinInfightParams.CycleTicks) {
            GoblinInfightParams.LeaderSwingTick ->
                swing(leader, leaderProfile, follower, followerProfile)
            GoblinInfightParams.LeaderBlowLandsTick -> land(follower, followerProfile)
            GoblinInfightParams.FollowerSwingTick ->
                swing(follower, followerProfile, leader, leaderProfile)
            GoblinInfightParams.FollowerBlowLandsTick -> land(leader, leaderProfile)
            else -> Unit
        }

        if (isBeatTick()) {
            taunt(leader, leaderProfile)
            taunt(follower, followerProfile)
        }

        tick++
    }

    private fun Bout.isBeatTick(): Boolean =
        when (tick % GoblinInfightParams.CycleTicks) {
            GoblinInfightParams.LeaderSwingTick,
            GoblinInfightParams.LeaderBlowLandsTick,
            GoblinInfightParams.FollowerSwingTick,
            GoblinInfightParams.FollowerBlowLandsTick -> true
            else -> false
        }

    private fun swing(
        attacker: Npc,
        attackerProfile: GoblinSoldier,
        defender: Npc,
        defenderProfile: GoblinSoldier,
    ) {
        attacker.faceNpc(defender)
        defender.faceNpc(attacker)

        attacker.anim(attackerProfile.attackSeq)
        defender.anim(defenderProfile.defendSeq)
        worldRepo.soundArea(
            source = attacker.coords,
            synth = attackerProfile.attackSound,
            radius = GoblinInfightParams.SoundRadius,
        )

        // Queue the hit on the swing tick; Npc.queue rejects a zero-cycle delay on the landing tick.
        val damage =
            if (random.randomBoolean(GoblinInfightParams.DamageOneIn)) {
                GoblinInfightParams.Damage
            } else {
                0
            }

        defender.queueHit(
            source = attacker,
            delay = GoblinInfightParams.HitDelay,
            type = HitType.Melee,
            damage = damage,
            modifier = hitModifier,
        )
    }

    private fun land(defender: Npc, defenderProfile: GoblinSoldier) {
        worldRepo.soundArea(
            source = defender.coords,
            synth = defenderProfile.defendSound,
            radius = GoblinInfightParams.SoundRadius,
        )
    }

    private fun taunt(goblin: Npc, profile: GoblinSoldier) {
        if (!random.randomBoolean(GoblinInfightParams.TauntOneIn)) {
            return
        }
        goblin.say(GoblinTaunts.of(profile.side).pick(random))
    }

    private fun Bout.stillValid(): Boolean {
        if (leader.uid != leaderUid || follower.uid != followerUid) {
            return false
        }
        if (!leader.isValidTarget() || !follower.isValidTarget()) {
            return false
        }
        if (leader.isBusy || follower.isBusy) {
            return false
        }
        if (!leader.isOutOfCombat() || !follower.isOutOfCombat()) {
            return false
        }
        if (leader.coords.chebyshevDistance(follower.coords) != 1) {
            return false
        }
        // Re-checked every tick, not just at engage: a door opening or closing between them changes
        // the answer without either goblin moving.
        return leader.canReach(follower)
    }

    private fun Bout.end() {
        bouts.remove(leaderUid)
        bouts.remove(followerUid)
        release(leader, leaderUid)
        release(follower, followerUid)
    }

    // Restore default AI only if this bout still owns the NPC; player combat may have claimed it.
    private fun release(goblin: Npc, uid: NpcUid) {
        if (goblin.uid != uid || !goblin.isSlotAssigned) {
            return
        }
        if (!goblin.isValidTarget()) {
            return
        }
        if (goblin.mode == NpcMode.None) {
            goblin.defaultMode()
        }
        reengageAt[uid] = goblin.currentMapClock + random.of(GoblinInfightParams.BreakOffCooldown)
    }

    private class Bout(
        val leader: Npc,
        val leaderProfile: GoblinSoldier,
        val follower: Npc,
        val followerProfile: GoblinSoldier,
    ) {
        val leaderUid: NpcUid = leader.uid
        val followerUid: NpcUid = follower.uid

        var tick: Int = 0
    }

    private companion object {
        const val BeatInterval = 1

        val CardinalOffsets = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}
