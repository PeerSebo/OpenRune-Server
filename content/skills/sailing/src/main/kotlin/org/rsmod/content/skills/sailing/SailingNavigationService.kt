package org.rsmod.content.skills.sailing

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.rsprot.protocol.game.outgoing.camera.CamTargetV4
import net.rsprot.protocol.game.outgoing.misc.client.ResetInteractionMode
import net.rsprot.protocol.game.outgoing.misc.client.SetInteractionMode
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.content.skills.sailing.SailingSessionState.helmInteractionWorld
import org.rsmod.content.skills.sailing.scripts.SailingSidepanel
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.type.getOrNull
import org.rsmod.map.CoordGrid

@Singleton
internal class SailingNavigationService
@Inject
constructor(
    private val boats: BoatWorldEntityService,
    private val seaMap: SailingSeaMap,
    private val mapClock: MapClock,
) {
    private val voyages = mutableMapOf<Player, Voyage>()

    fun registerTutorial(player: Player, entity: WorldEntity) {
        check(boats.isTutorial(player) && boats.boarded(player) === entity)
        val boat = org.rsmod.content.skills.sailing.configs.SailingBoats.skiff
        val stats = listOf(
            org.rsmod.content.skills.sailing.configs.SailingBoats.baseHullStats(boat),
            boat.sailOption.minBy { it.sailingRequirement }.facilityStats,
        )
        val base = stats.sumOf { it.boatBasespeed ?: 0 }
        val cap = stats.sumOf { it.boatSpeedcap ?: 0 }
        voyages[player] = Voyage(
            entity, 0,
            SailingMotionStats(if (cap > 0) minOf(base, cap) else base,
                64 + stats.sumOf { it.boatAcceleration ?: 0 },
                stats.sumOf { it.boatSpeedboostDuration ?: 0 }.coerceAtLeast(1)),
            SailingWind.TRIM_XP.first(),
        )
    }

    fun isNavigating(player: Player): Boolean = current(player)?.helmCoords != null

    fun releaseHelm(player: Player) {
        val voyage = current(player) ?: return
        if (voyage.helmCoords != null) leaveHelm(player, voyage)
    }

    fun navigate(access: ProtectedAccess) {
        val player = access.player
        val root = boats.boarded(player)?.rootCoords
        if (root != null && root.x / 64 == 32 && root.z / 64 == 42) {
            access.mes("Your boat must leave the shipyard before you can sail it.")
            return
        }
        val voyage = voyage(player) ?: run {
            access.mes("You must be aboard your own boat to use the helm.")
            return
        }
        if (voyage.helmCoords != null) {
            val wasAtHelm = atHelm(player, voyage)
            leaveHelm(player, voyage)
            if (wasAtHelm) {
                access.mes("You step away from the helm.")
                return
            }
        }
        player.routeDestination.clear()
        voyage.helmCoords = player.coords
        player.anim(helmPose(voyage.entity.configId, loop = false))
        voyage.nextPose = mapClock.cycle + HELM_POSE_TICKS
        player.sailingFacilityLockedIn = 3
        player.writeSailingVar(VAR_AT_HELM, 1)
        player.writeSailingVar(VAR_HELM_STATUS, 2)
        if (voyage.slot == 0) SailingSidepanel.openTutorial(access)
        else SailingSidepanel.open(access, voyage.slot, SailingSidepanel.Tab.FACILITIES)
        // Enable Heading tile mode explicitly; camera and helm varbits alone leave water clicks as walking.
        player.helmInteractionWorld = voyage.entity.slotId
        player.client.write(SetInteractionMode(DEFAULT_INTERACTION_WORLD, TILE_HEADING, ENTITIES_ENABLED))
        player.client.write(SetInteractionMode(voyage.entity.slotId, TILE_WALK, ENTITIES_ENABLED))
        player.client.write(CamTargetV4(CamTargetV4.WorldEntityTarget(voyage.entity.slotId)))
        sync(player, voyage)
        access.mes("You take the helm. Choose a heading and set your sails to get underway.")
    }

    fun heading(player: Player, heading: Int) {
        if (heading !in 0..15) return
        val voyage = captain(player) ?: return
        voyage.motion.heading(heading)
        if (player.readSailingVar("varbit.sailing_start_boat_when_setting_heading") != 0) {
            voyage.motion.forward()
        }
        sync(player, voyage)
    }

    fun control(player: Player, button: Int) {
        val voyage = captain(player) ?: return
        when (button) {
            0 -> voyage.motion.toggle()
            1 -> if (!voyage.motion.decrease()) player.mes("The boat is already reversing.")
            2 -> if (!voyage.motion.increase()) player.mes("The boat is already going as fast as it can.")
            else -> return
        }
        sync(player, voyage)
    }

    fun setSails(player: Player, set: Boolean) {
        val voyage = voyage(player) ?: return
        if (set) voyage.motion.forward() else voyage.motion.stop()
        sync(player, voyage)
        player.mes(if (set) "You set the sails." else "You unset the sails.")
    }

    fun trim(player: Player) {
        val voyage = current(player) ?: return
        val held = getOrNull(player.worn[Wearpos.RightHand.slot])
        if (held != null && SailingCargoHold.isCourierCargo(held)) {
            player.mes("Put down your cargo before trimming the sails.")
            return
        }
        if (!voyage.wind.trim()) {
            player.mes("The sails are already catching the wind.")
            return
        }
        voyage.motion.boost(mapClock.cycle, voyage.stats)
        player.statAdvance("stat.sailing", voyage.trimXp)
        player.mes("You trim the sails to catch the wind.")
        sync(player, voyage)
    }

    fun halt(player: Player) {
        val voyage = current(player) ?: return
        voyage.motion.halt()
        voyage.motion.heading(voyage.entity.angle / 128)
        voyage.wind.reset()
        sync(player, voyage)
    }

    fun tick() {
        val iterator = voyages.entries.iterator()
        while (iterator.hasNext()) {
            val (player, voyage) = iterator.next()
            if (!valid(player, voyage)) {
                if (player.isSlotAssigned) clearControls(player)
                iterator.remove()
                continue
            }
            if (voyage.helmCoords != null && !atHelm(player, voyage)) leaveHelm(player, voyage)
            if (voyage.helmCoords != null && mapClock.cycle >= voyage.nextPose) {
                player.anim(helmPose(voyage.entity.configId, loop = true))
                voyage.nextPose = mapClock.cycle + HELM_POSE_TICKS
            }
            val step = voyage.motion.step(voyage.entity.angle, mapClock.cycle, voyage.stats)
            val entity = voyage.entity
            val moves = step.dx != 0 || step.dz != 0 || step.angle != entity.angle
            if (moves && !seaMap.canMove(entity, entity.fineX + step.dx, entity.fineZ + step.dz, step.angle)) {
                voyage.motion.halt()
                voyage.motion.heading(entity.angle / 128)
                voyage.wind.reset()
                player.mes("There isn't enough clear water for your boat in that direction.")
            } else {
                entity.moveRoot(entity.fineX + step.dx, entity.fineZ + step.dz)
                entity.angle = step.angle
                if (voyage.wind.tick(voyage.motion.speed > 0)) {
                    player.mes("A gust of wind catches your sails. Trim them to pick up speed!")
                }
            }
            sync(player, voyage)
        }
    }

    private fun voyage(player: Player): Voyage? {
        current(player)?.let { return it }
        val entity = boats.boarded(player) ?: return null
        if (!player.isOnPlayerBoat() || player.coords !in entity || entity.ownerSlot != player.slotId) return null
        val slot = player.activeBoatSlot() ?: return null
        if (boats.boardedSlot(player) != slot) return null
        val status = SailingSidepanel.status(player, slot) ?: return null
        if (status.boat.typeId != player.boardedBoatType ||
            BoatHullEntity.byConfigId(entity.configId)?.typeId != status.boat.typeId ||
            status.sail == null || status.steering == null) return null
        val stats = status.statRows
        val base = stats.sumOf { it.boatBasespeed ?: 0 }
        val cap = stats.sumOf { it.boatSpeedcap ?: 0 }
        if (base <= 0) return null
        val sailIndex = status.boat.sailOption.sortedBy { it.sailingRequirement }.indexOfFirst { it.rowId == status.sail.rowId }
        val xp = SailingWind.TRIM_XP.getOrNull(sailIndex) ?: return null
        return Voyage(
            entity, slot,
            SailingMotionStats(if (cap > 0) minOf(base, cap) else base,
                64 + stats.sumOf { it.boatAcceleration ?: 0 },
                (stats.sumOf { it.boatSpeedboostDuration ?: 0 }).coerceAtLeast(1)),
            xp,
        ).also { voyages[player] = it }
    }

    private fun current(player: Player): Voyage? = voyages[player]?.takeIf { valid(player, it) }

    private fun captain(player: Player): Voyage? = current(player)?.takeIf { atHelm(player, it) }

    private fun valid(player: Player, voyage: Voyage): Boolean =
        player.isSlotAssigned && player.isOnPlayerBoat() && boats.boarded(player) === voyage.entity &&
            boats.boardedSlot(player) == voyage.slot &&
            player.coords in voyage.entity && voyage.entity.ownerSlot == player.slotId &&
            (voyage.slot == 0 || (player.sailingSelectedBoatSlot == voyage.slot &&
            player.ownsBoat(voyage.slot) &&
            player.readSailingVar(SailingBoatVars.field(voyage.slot, "type")) == player.boardedBoatType))

    private fun atHelm(player: Player, voyage: Voyage): Boolean =
        voyage.helmCoords != null && player.coords == voyage.helmCoords &&
            player.routeDestination.isEmpty() && SailingSidepanel.INTERFACE in player.ui

    private fun leaveHelm(player: Player, voyage: Voyage) {
        voyage.helmCoords = null
        clearControls(player)
    }

    private fun sync(player: Player, voyage: Voyage) {
        player.writeSailingVar(VAR_MOVE_MODE, voyage.motion.mode.value)
        player.writeSailingVar("varbit.sailing_boat_time_till_trim", voyage.wind.untilOffer)
        player.writeSailingVar("varbit.sailing_boat_time_trim_window", voyage.wind.window)
        player.writeSailingVar("varbit.sailing_boat_speedboost_duration", (voyage.motion.windUntil - mapClock.cycle).coerceAtLeast(0))
        player.writeSailingVar("varbit.sailing_boat_winds_timer_paused", if (voyage.motion.speed > 0) 0 else 1)
    }

    private class Voyage(val entity: WorldEntity, val slot: Int, val stats: SailingMotionStats, val trimXp: Double) {
        val motion = SailingMotion(entity.angle)
        val wind = SailingWind()
        var helmCoords: CoordGrid? = null
        var nextPose: Int = 0
    }

    companion object {
        const val COMPONENT_CONTROLS = "component.sailing_sidepanel:facilities_content_clicklayer"
        const val VAR_MOVE_MODE = "varbit.sailing_sidepanel_boat_move_mode"
        const val VAR_AT_HELM = "varbit.sailing_sidepanel_player_at_helm"
        const val VAR_HELM_STATUS = "varbit.sailing_sidepanel_helm_status"

        private const val HELM_POSE_TICKS = 10

        private const val DEFAULT_INTERACTION_WORLD = -2
        private const val TILE_WALK = 1
        private const val TILE_HEADING = 2
        private const val ENTITIES_ENABLED = 1

        private fun helmPose(configId: Int, loop: Boolean): String {
            val base = when (configId) {
                1 -> "seq.human_sailing_alpha_helm_raft01_active01"
                2 -> "seq.human_sailing_alpha_helm_small01_active01"
                3 -> "seq.human_sailing_helm_3x8_active01"
                else -> error("Unsupported Sailing hull config: $configId")
            }
            return base + if (loop) "_loop" else ""
        }

        fun clearControls(player: Player) {
            val interactionWorld = player.helmInteractionWorld
            player.helmInteractionWorld = null
            if (interactionWorld != null && player.isSlotAssigned) {
                player.client.write(ResetInteractionMode(interactionWorld))
                player.client.write(SetInteractionMode(DEFAULT_INTERACTION_WORLD, TILE_WALK, ENTITIES_ENABLED))
            }
            // Preserve the animation of the replacement action.
            val pending = player.pendingSequence
            val helmAnimation = (1..3).any { config ->
                pending.id == helmPose(config, false).asRSCM(RSCMType.SEQ) ||
                    pending.id == helmPose(config, true).asRSCM(RSCMType.SEQ)
            }
            if (player.readSailingVar(VAR_AT_HELM) == 1 && (pending == EntitySeq.NULL || helmAnimation)) {
                player.resetAnim()
            }
            player.sailingFacilityLockedIn = 0
            player.writeSailingVar(VAR_AT_HELM, 0)
            player.writeSailingVar(VAR_HELM_STATUS, 0)
            if (player.isSlotAssigned) player.client.write(CamTargetV4(CamTargetV4.PlayerCamTarget(player.slotId)))
        }
    }
}
