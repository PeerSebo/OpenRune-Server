package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.constants
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.NonInterruptingComponents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerHit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.utils.time.epochMinute
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SpellTeleportScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
    private val worldRepo: WorldRepository,
) : PluginScript(), PlayerPostTickHook, PlayerDeathCleanupHook {
    // Persist Home Teleport cooldown in epoch minutes; MapClock resets on restart.
    private var Player.homeTeleportCooldown by intVarp("varp.aide_tele_timer")

    override fun ScriptContext.startup() {
        onPlayerLogin { HomeTeleportState.clear(player) }
        onPlayerLogout { HomeTeleportState.clear(player) }
        for (teleport in StandardSpellTeleports.entries) {
            val spell = teleport.resolveSpell() ?: continue
            if (teleport.isHome) {
                // Validate before cancelling the current action; denied Home clicks must not unwind obstacles.
                NonInterruptingComponents.register(
                    RSCM.getReverseMapping(RSCMType.COMPONENT, spell.component.packed)
                )
            }
            onIfOverlayButton(spell.component) { castSpellTeleport(spell, teleport, it.op) }
        }
        onPlayerQueueWithArgs<PendingSpellTeleport>(TeleportQueue) {
            processQueuedTeleport(it.args)
        }
        onPlayerHit { player.abortHomeChannel() }
    }

    override fun onPostTick(player: Player) {
        val castCoords = HomeTeleportState.castCoords(player) ?: return
        if (player.homeChannelBroken(castCoords)) {
            player.abortHomeChannel()
        }
    }

    override fun cleanup(player: Player) {
        HomeTeleportState.clear(player)
    }

    private fun StandardSpellTeleport.resolveSpell(): MagicSpell? {
        return spells.getObjSpell(spellObj)
    }

    private suspend fun ProtectedAccess.castSpellTeleport(
        spell: MagicSpell,
        teleport: StandardSpellTeleport,
        op: IfButtonOp,
    ) {
        if (teleport.isHome) {
            val denial = player.homeTeleportDenial()
            if (denial != null) {
                mes(denial)
                return
            }
        }
        if (actionDelay > mapClock) {
            return
        }

        if (!teleport.canCast(this)) {
            return
        }

        if (teleport.isHome && !homeTeleportOffCooldown()) {
            return
        }

        val option = teleport.option(op)
        val destination = teleport.destination(spell, option)
        if (destination == null) {
            mes(option.missingDestinationMessage)
            return
        }

        if (!canTeleport()) {
            return
        }

        if (!consumeRequirements(spell, teleport)) {
            return
        }

        // A replacement teleport must not inherit Home's queued landing.
        player.abortHomeChannel()
        actionDelay = mapClock + TeleportActionDelay
        ifClose()
        player.abortRoute()
        player.clearMapFlag()

        if (teleport.isHome) {
            clearQueue(TeleportQueue)
            HomeTeleportState.start(player, player.coords.packed)
            playHomeTeleportStage(0)
            queueHomeTeleport(
                teleport,
                destination.packed,
                castCoords = player.coords.packed,
            )
            return
        }

        if (spell.spellbook == Spellbook.Arceuus) {
            anim("seq.human_spellcast_arceuus_teleport")
            spotanim("spotanim.arceuus_teleport_spotanim", height = 0)
        } else if (spell.spellbook == Spellbook.Lunars) {
            anim("seq.lunar_human_magic_summon1")
            spotanim("spotanim.lunar_teleport_spotanim", height = 0)
        } else if (teleport.ancientStyle) {
            anim("seq.zaros_vertical_casting")
            spotanim("spotanim.zaros_teleport", height = 0)
        } else {
            anim(TeleportStartAnim)
            spotanim(TeleportSpotanim, height = TeleportSpotanimHeight)
        }
        soundSynth(TeleportSound)
        // Queue the landing independently; protected coroutine cancellation must not leave a pending channel.
        clearQueue(TeleportQueue)
        // Interface buttons run before queue decrement; compensate with +1.
        val delay = if (teleport.ancientStyle) AncientTeleportDelay else TeleportDelay
        queue(TeleportQueue, delay + 1, PendingSpellTeleport(teleport, destination.packed))
    }

    private fun Player.homeTeleportDenial(): String? = when {
        homeCombatBlocked() -> "You can't use Home Teleport until 10 seconds after the end of combat."
        homeActionBlocked() || HomeTeleportState.castCoords(this) != null -> constants.dm_busy
        else -> null
    }

    private fun Player.homeCombatBlocked(): Boolean {
        val lastCombat = maxOf(vars["varp.lastcombat"], vars["varp.lastcombat_pvp"])
        val recentlyAttacked = lastCombat > 0 && currentMapClock - lastCombat < HomeCombatQuietTicks
        // Successful kills may clear lastcombat; incoming hits retain the logout lock.
        return recentlyAttacked || preventLogoutUntil >= currentMapClock
    }

    private fun Player.homeActionBlocked(): Boolean =
        isAccessProtected || isBusy2 || routeRequest != null || forcedRoute ||
            pendingExactMove != null || pendingTeleport || pendingTelejump ||
            hasMovedThisCycle ||
            weakQueueList.isNotEmpty || queueList.size > queueList.count(TeleportQueue)

    private fun ProtectedAccess.homeTeleportOffCooldown(): Boolean {
        val remaining = player.homeTeleportCooldown - epochMinute()
        if (remaining <= 0) {
            return true
        }
        val unit = if (remaining == 1) "minute" else "minutes"
        mes("You need to wait another $remaining $unit to cast this spell.")
        return false
    }

    private fun ProtectedAccess.playHomeTeleportStage(index: Int) {
        val stage = HomeTeleportStages[index]
        anim(stage.seq)
        if (stage.spotanim != null) {
            spotanim(stage.spotanim, height = stage.spotanimHeight)
        }
        if (stage.sound != null) {
            soundArea(worldRepo, player.coords, stage.sound, radius = HomeTeleportSoundRadius)
        }
    }

    private fun ProtectedAccess.queueHomeTeleport(
        teleport: StandardSpellTeleport,
        destination: Int,
        castCoords: Int,
    ) {
        for (index in 1 until HomeTeleportStages.size) {
            queue(
                TeleportQueue,
                HomeTeleportStages[index].castOffset + 1,
                PendingSpellTeleport(teleport, destination, index, castCoords),
            )
        }
        queue(
            TeleportQueue,
            HomeTeleportLandingOffset + 1,
            PendingSpellTeleport(teleport, destination, LandingStage, castCoords),
        )
    }

    private fun Player.homeChannelBroken(task: PendingSpellTeleport): Boolean {
        val castCoords = task.castCoords ?: return false
        return HomeTeleportState.castCoords(this) != castCoords || homeChannelBroken(castCoords)
    }

    private fun Player.homeChannelBroken(castCoords: Int): Boolean {
        val otherAnimation = pendingSequence != EntitySeq.NULL && pendingSequence != EntitySeq.ZERO &&
            HomeTeleportStages.none { it.seq.asRSCM(RSCMType.SEQ) == pendingSequence.id }
        return coords.packed != castCoords || homeCombatBlocked() || homeActionBlocked() || otherAnimation
    }

    private fun Player.abortHomeChannel() {
        if (HomeTeleportState.castCoords(this) == null) return
        HomeTeleportState.clear(this)
        queueList.removeAll(TeleportQueue)
        // Preserve the replacement action's animation/effect.
        if (pendingSequence == EntitySeq.NULL || HomeTeleportStages.any { it.seq.asRSCM(RSCMType.SEQ) == pendingSequence.id }) {
            resetAnim()
        }
        val hasReplacementSpot = pendingSpotanims.any { packed ->
            val spot = EntitySpotanim(packed)
            spot.slot == 0 && spot.id != 65535 && HomeTeleportStages.none {
                it.spotanim?.asRSCM(RSCMType.SPOTANIM) == spot.id
            }
        }
        if (!hasReplacementSpot) {
            for (index in pendingSpotanims.size - 1 downTo 0) {
                if (EntitySpotanim(pendingSpotanims.getLong(index)).slot == 0) {
                    pendingSpotanims.removeLong(index)
                }
            }
            pendingSpotanims.add(EntitySpotanim(65535, 0, 0, 0).packed)
        }
    }

    private fun ProtectedAccess.processQueuedTeleport(task: PendingSpellTeleport) {
        val spell = task.teleport.resolveSpell() ?: return

        if (player.homeChannelBroken(task)) {
            player.abortHomeChannel()
            return
        }

        if (task.stage in HomeTeleportStages.indices) {
            playHomeTeleportStage(task.stage)
            return
        }

        if (!canTeleport()) {
            resetAnim()
            clearHomeTeleportSpotanim(task.teleport)
            HomeTeleportState.clear(player)
            return
        }
        HomeTeleportState.clear(player)
        telejump(CoordGrid(task.destination))
        resetAnim()
        clearHomeTeleportSpotanim(task.teleport)
        if (task.teleport.isHome) {
            player.homeTeleportCooldown = epochMinute() + HomeTeleportCooldownMinutes
        }
        statAdvance("stat.magic", spell.castXp)
        publish(PlayerActionCompleteEvent(
            player, PlayerAction.CastTeleport,
            task.teleport.spellObj.internalName, CoordGrid(task.destination),
        ))
    }

    // Clear the portal explicitly on landing; its animation outlives the channel.
    private fun ProtectedAccess.clearHomeTeleportSpotanim(teleport: StandardSpellTeleport) {
        if (teleport.isHome) {
            spotanim(null)
        }
    }

    private fun ProtectedAccess.canTeleport(): Boolean {
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial == null) {
            return true
        }
        player.clearMapFlag()
        player.mes(denial, ChatType.Engine)
        return false
    }

    private fun ProtectedAccess.consumeRequirements(
        spell: MagicSpell,
        teleport: StandardSpellTeleport,
    ): Boolean {
        val castSpell =
            if (teleport == StandardSpellTeleports.apeAtoll) {
                val banana = ServerCacheManager.getItem(Banana.asRSCM(RSCMType.OBJ)) ?: return false
                val spellWithoutBanana = spell.copy(objReqs = spell.objReqs.withoutBananaReq())
                if (!runes.canCastSpell(player, spellWithoutBanana)) {
                    return false
                }
                if (!deleteBanana(banana)) {
                    mes("You need a banana to cast this spell.")
                    return false
                }
                spellWithoutBanana
            } else {
                spell
            }

        if (castSpell.objReqs.isEmpty()) {
            return runes.canCastSpell(player, castSpell)
        }
        return !runes.attemptCast(player, castSpell).isFailure()
    }

    private fun List<MagicSpell.ObjRequirement>.withoutBananaReq(): List<MagicSpell.ObjRequirement> {
        return filterNot { RSCM.getReverseMapping(RSCMType.OBJ, it.obj.id).contains("banana") }
    }

    private fun ProtectedAccess.deleteBanana(banana: ItemServerType): Boolean {
        val bananaSlot = inv.indexOfFirst { it.isType(banana) }
        if (bananaSlot == -1) {
            return false
        }
        val transaction =
            player.invTransaction(inv, autoCommit = true) {
                val targetInv = select(inv)
                delete {
                    from = targetInv
                    obj = banana.id
                    strictCount = 1
                    strictSlot = bananaSlot
                }
            }
        return transaction.success
    }

    private fun StandardSpellTeleport.canCast(access: ProtectedAccess): Boolean {
        val questKey = requiredQuest ?: return true
        if (QuestRequirements.hasCompleted(access.player, questKey)) {
            return true
        }
        access.mes(lockedMessage)
        return false
    }

    private data class PendingSpellTeleport(
        val teleport: StandardSpellTeleport,
        val destination: Int,
        val stage: Int = LandingStage,
        val castCoords: Int? = null,
    )

    private data class HomeTeleportStage(
        val castOffset: Int,
        val seq: String,
        val spotanim: String? = null,
        val spotanimHeight: Int = 0,
        val sound: String? = null,
    )

    private companion object {
        private val TeleportStartAnim = RSCM.getReverseMapping(RSCMType.SEQ, 714)
        private val TeleportSpotanim = RSCM.getReverseMapping(RSCMType.SPOTANIM, 111)
        private const val Banana = "obj.banana"
        private const val TeleportQueue = "queue.spell_teleport"
        private const val TeleportSound = "synth.TELEPORT_ALL"
        private const val TeleportSpotanimHeight = 92

        private const val TeleportDelay = 3
        private const val AncientTeleportDelay = 4
        private const val TeleportActionDelay = 5

        private val HomeTeleportStages =
            listOf(
                HomeTeleportStage(
                    castOffset = 0,
                    seq = "seq.aide_drawing_chalk_circle",
                    spotanim = "spotanim.aide_chalk_circle",
                    sound = "synth.AIDE_TELEPORT_CHALK",
                ),
                HomeTeleportStage(
                    castOffset = 6,
                    seq = "seq.aide_sitting_down_crosslegged",
                    sound = "synth.AIDE_TELEPORT_SITDOWN",
                ),
                HomeTeleportStage(
                    castOffset = 12,
                    seq = "seq.aide_player_getting_book",
                    spotanim = "spotanim.aide_player_book_get",
                    sound = "synth.AIDE_TELEPORT_BOOK",
                ),
                HomeTeleportStage(
                    castOffset = 16,
                    seq = "seq.aide_reciting_incantation",
                    spotanim = "spotanim.aide_player_book_portal",
                    // Height is unsigned 16-bit; preserve the raw 65526 value.
                    spotanimHeight = 65526,
                    sound = "synth.AIDE_TELEPORT_PORTAL",
                ),
                HomeTeleportStage(
                    castOffset = 20,
                    seq = "seq.aide_player_teleporting",
                    spotanim = "spotanim.aide_player_teleport",
                ),
            )

        private const val HomeTeleportLandingOffset = 23
        private const val HomeTeleportCooldownMinutes = 30
        private const val HomeTeleportSoundRadius = 4
        private const val HomeCombatQuietTicks = 17

        private const val LandingStage = -1
    }
}
