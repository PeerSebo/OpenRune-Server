package org.rsmod.api.player.hit.processor

import kotlin.math.min
import org.rsmod.api.player.death.recordDeathCause
import org.rsmod.api.player.death.resolveDeathCause
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.events.PlayerHitpointsChangedEvent
import org.rsmod.api.player.headbar.InternalPlayerHeadbars
import org.rsmod.api.player.hit.playDefendSound
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType

public object StandardPlayerHitProcessor : QueuedPlayerHitProcessor {
    override fun ProtectedAccess.process(hit: Hit) {
        if (!hit.isValid(this)) {
            return
        }
        preventLogout("You can't log out until 10 seconds after the end of combat.", 16)

        // TODO(combat): Process degradation, ring of recoil, retribution, etc.

        val oldHitpoints = player.hitpoints
        val damage = min(oldHitpoints, hit.damage)
        if (damage > 0) {
            statSub("stat.hitpoints", constant = damage, percent = 0)
            recordHitDamage(player, hit, damage)
            publishPlayerHitpointsChangedEvent(oldHitpoints, hit)
        }

        playAndRelayDefendSound(hit)

        val queueDeath = player.hitpoints == 0 && "queue.death" !in player.queueList
        if (queueDeath) {
            val npcSource = if (hit.isFromNpc) findHitNpcSource(hit) else null
            val playerSource = if (hit.isFromPlayer) findHitPlayerSource(hit) else null
            player.recordDeathCause(hit.resolveDeathCause(npcSource, playerSource))
            queueDeath()
        }

        player.showHitmark(hit.hitmark)

        val headbar = hit.createHeadbar(player.hitpoints, player.baseHitpointsLvl)
        player.showHeadbar(headbar)

        publish(PlayerHitEvents.Impact(player, hit))
    }

    private fun ProtectedAccess.publishPlayerHitpointsChangedEvent(oldHitpoints: Int, hit: Hit) {
        val event =
            PlayerHitpointsChangedEvent(
                player = player,
                oldHitpoints = oldHitpoints,
                newHitpoints = player.hitpoints,
                maxHitpoints = player.baseHitpointsLvl,
                hit = hit,
            )
        publish(event)
    }

    private fun Hit.isValid(access: ProtectedAccess): Boolean {
        // Currently, we only have evidence of this validation being applied to hits dealt by npcs.
        if (!isFromNpc) {
            return true
        }
        if (type != HitType.Melee) {
            return true
        }
        val npc = access.findHitNpcSource(this) ?: return false
        return npc.hitpoints > 0
    }

    /**
     * Named distinctly from the shared [playDefendSound] extension: a member function shadows a
     * top-level extension of the same name, so calling it `playDefendSound(hit: Hit)` made the call
     * below resolve back to itself instead of to the shared resolver.
     */
    private fun ProtectedAccess.playAndRelayDefendSound(hit: Hit) {
        val defendSound = playDefendSound(hit.damage, delay = DEFEND_SOUND_DELAY)

        val playerSource = if (hit.isFromPlayer) findHitPlayerSource(hit) else null
        playerSource?.soundSynth(defendSound)

        // Everyone else with the zone loaded hears it as a `soundarea`, so a bystander standing
        // next to the fight hears the fight. Both participants already received it as a
        // player-local `soundsynth` above and are cut out of the broadcast - live never delivers
        // the same combat sound to one client both ways.
        worldRepo.soundAreaExcept(
            source = player,
            synth = defendSound,
            except = listOf(player, playerSource),
            delay = DEFEND_SOUND_DELAY,
            radius = WorldRepository.COMBAT_SOUND_RADIUS,
        )
    }

    private fun Hit.createHeadbar(currHp: Int, maxHp: Int): Headbar =
        InternalPlayerHeadbars.createFromHitmark(hitmark, currHp, maxHp, "headbar.health_30")

    /**
     * Client-side synth delay, in client ticks, for the defend sound. Matches the default that
     * [playDefendSound] already used; named here so the local synth and the area broadcast cannot
     * drift apart and play at different moments for the participant and a bystander.
     */
    private const val DEFEND_SOUND_DELAY: Int = 20
}
