package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.message.OutgoingGameMessage
import net.rsprot.protocol.message.ZoneProt
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.registry.zone.ZoneUpdateTransformer
import org.rsmod.api.utils.zone.SharedZoneEnclosedBuffers
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.map.zone.ZoneKey

/** Sent after SetActiveWorld and the deck rebuild, while that boat is the active client world. */
public class WorldEntityZoneUpdates
@Inject
constructor(
    private val locs: LocRegistry,
    private val objs: ObjRegistry,
    private val updates: ZoneUpdateMap,
    private val enclosed: SharedZoneEnclosedBuffers,
) {
    public fun send(
        player: Player,
        world: WorldEntity,
        added: Boolean,
        write: (OutgoingGameMessage) -> Unit,
    ) {
        val base = world.instanceSouthWest
        val observerLevel = if (player.coords in world) player.level else -1
        for (x in 0 until world.zoneSizeX) for (z in 0 until world.zoneSizeZ) {
            for (level in world.minLevel..world.maxLevel) {
                val zone = ZoneKey((base.x shr 3) + x, (base.z shr 3) + z, level)
                if (added) {
                    write(UpdateZoneFullFollows(x * 8, z * 8, level))
                    val persistent = mutableListOf<ZoneProt>()
                    locs.findAllSpawned(zone).forEach {
                        persistent += ZoneUpdateTransformer.toPersistentLocChange(it)
                    }
                    objs.findAll(zone).forEach {
                        ZoneUpdateTransformer.toPersistentObjAdd(it, player.observerUUID)?.let(persistent::add)
                    }
                    sendEnclosed(persistent, x * 8, z * 8, level, write)
                } else {
                    val changes = updates[zone] ?: continue
                    val visible = changes.mapNotNull { visibleUpdate(it, player, level, observerLevel) }
                    if (visible.isEmpty()) continue
                    sendEnclosed(visible, x * 8, z * 8, level, write)
                }
            }
        }
    }

    private fun sendEnclosed(
        changes: List<ZoneProt>,
        x: Int,
        z: Int,
        level: Int,
        write: (OutgoingGameMessage) -> Unit,
    ) {
        if (changes.isEmpty()) return
        val buffer = enclosed.computeBufferForClient(OldSchoolClientType.DESKTOP, changes)
        write(UpdateZonePartialEnclosed(x, z, level, buffer))
    }

    private fun visibleUpdate(
        update: ZoneProt,
        player: Player,
        level: Int,
        observerLevel: Int,
    ): ZoneProt? {
        if (level != observerLevel && ZoneUpdateTransformer.isAreaSound(update)) return null
        return when (update) {
            is ZoneUpdateTransformer.ObjPrivateZoneProt ->
                if (update.isVisibleTo(player.observerUUID)) update.backing else null
            is ZoneUpdateTransformer.ObjReveal ->
                if (player.observerUUID == update.obj.receiverId) null else update.backing
            is ZoneUpdateTransformer.SoundAreaExcluded ->
                if (update.isAudibleTo(player.uid.packed, observerLevel)) update.backing else null
            is ZoneUpdateTransformer.PartialFollowsZoneProt -> update.backing
            else -> update
        }
    }
}
