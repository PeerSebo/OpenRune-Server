package org.rsmod.api.repo.world

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.types.aconverted.SynthType
import jakarta.inject.Inject
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

public class WorldRepository @Inject constructor(private val zoneUpdates: ZoneUpdateMap) {
    public fun locAnim(loc: LocInfo, seq: String) {
        zoneUpdates.locAnim(loc, seq.asRSCM(RSCMType.SEQ))
    }

    public fun locAnim(loc: BoundLocInfo, seq: String) {
        locAnim(LocInfo(loc.layer, loc.coords, loc.entity), seq)
    }

    public fun locMerge(
        loc: LocInfo,
        player: Player,
        start: Int,
        end: Int,
        minX: Int = 0,
        minZ: Int = 0,
        maxX: Int = 0,
        maxZ: Int = 0,
    ) {
        if (player.staffInvisible) return
        zoneUpdates.locMerge(loc, player.slotId, start, end, minX, minZ, maxX, maxZ)
    }

    public fun locMerge(
        loc: BoundLocInfo,
        player: Player,
        start: Int,
        end: Int,
        minX: Int = 0,
        minZ: Int = 0,
        maxX: Int = 0,
        maxZ: Int = 0,
    ) {
        locMerge(
            LocInfo(loc.layer, loc.coords, loc.entity),
            player,
            start,
            end,
            minX,
            minZ,
            maxX,
            maxZ,
        )
    }

    /** Area sounds must stay on the source plane; client attenuation ignores height. */
    public fun soundArea(
        source: CoordGrid,
        synth: String,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
        size: Int = 0,
    ) {
        zoneUpdates.soundArea(source, synth.asRSCM(RSCMType.SYNTH), delay, loops, radius, size)
    }

    public fun soundArea(
        source: PathingEntity,
        synth: String,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
    ) {
        if (source is Player && source.staffInvisible) {
            source.soundSynth(synth, loops, delay)
            return
        }
        soundArea(source.coords, synth, delay, loops, radius, source.size)
    }

    public fun soundArea(
        source: CoordGrid,
        synth: Int,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
        size: Int = 0,
    ) {
        zoneUpdates.soundArea(source, synth, delay, loops, radius, size)
    }

    public fun soundArea(
        source: PathingEntity,
        synth: Int,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
    ) {
        if (source is Player && source.staffInvisible) {
            source.soundSynth(synth, loops, delay)
            return
        }
        soundArea(source.coords, synth, delay, loops, radius, source.size)
    }

    // Participants receive soundsynth; exclude them from soundarea to avoid double playback.
    // The two channels use separate client volume sliders.
    public fun soundAreaExcept(
        source: CoordGrid,
        synth: Int,
        except: Player?,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
        size: Int = 0,
    ) {
        val uid = except?.uid
        val excluded =
            if (uid == null || uid == PlayerUid.NULL) EMPTY_UIDS else intArrayOf(uid.packed)
        zoneUpdates.soundAreaExcluding(source, synth, delay, loops, radius, size, excluded)
    }

    public fun soundAreaExcept(
        source: CoordGrid,
        synth: Int,
        except: Collection<Player?>,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
        size: Int = 0,
    ) {
        val excluded = except.packUids()
        zoneUpdates.soundAreaExcluding(source, synth, delay, loops, radius, size, excluded)
    }

    public fun soundAreaExcept(
        source: PathingEntity,
        synth: SynthType,
        except: Player?,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
    ) {
        if (source is Player && source.staffInvisible) return
        soundAreaExcept(source.coords, synth.id, except, delay, loops, radius, source.size)
    }

    public fun soundAreaExcept(
        source: PathingEntity,
        synth: SynthType,
        except: Collection<Player?>,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
    ) {
        if (source is Player && source.staffInvisible) return
        soundAreaExcept(source.coords, synth.id, except, delay, loops, radius, source.size)
    }

    public fun soundAreaExcept(
        source: PathingEntity,
        synth: String,
        except: Player?,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
    ) {
        if (source is Player && source.staffInvisible) return
        val id = synth.asRSCM(RSCMType.SYNTH)
        soundAreaExcept(source.coords, id, except, delay, loops, radius, source.size)
    }

    private fun Collection<Player?>.packUids(): IntArray {
        if (isEmpty()) {
            return EMPTY_UIDS
        }
        var count = 0
        val packed = IntArray(size)
        for (player in this) {
            val uid = player?.uid ?: continue
            if (uid == PlayerUid.NULL) {
                continue
            }
            var duplicate = false
            for (i in 0 until count) {
                if (packed[i] == uid.packed) {
                    duplicate = true
                    break
                }
            }
            if (!duplicate) {
                packed[count++] = uid.packed
            }
        }
        return if (count == packed.size) packed else packed.copyOf(count)
    }

    public fun spotanimMap(
        spotanim: SpotanimType,
        coord: CoordGrid,
        height: Int = 0,
        delay: Int = 0,
    ) {
        zoneUpdates.mapAnim(spotanim.id, coord, height, delay)
    }

    public fun projAnim(projAnim: ProjAnim) {
        zoneUpdates.mapProjAnim(projAnim)
    }

    public fun projAnim(
        source: Player,
        target: Npc,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToNpc(source.bounds(), target, spotanim.id, type)
        if (!source.staffInvisible) projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Player,
        target: Npc,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromPlayerToNpc(source, target, spotanim.id, type)
        if (!source.staffInvisible) projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Player,
        target: Player,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToPlayer(source.bounds(), target, spotanim.id, type)
        if (!source.staffInvisible && !target.staffInvisible) projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Player,
        target: Player,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromPlayerToPlayer(source, target, spotanim.id, type)
        if (!source.staffInvisible && !target.staffInvisible) projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Player,
        target: CoordGrid,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToCoord(source.bounds(), target, spotanim.id, type)
        if (!source.staffInvisible) projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Player,
        target: CoordGrid,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromPlayerToCoord(source, target, spotanim.id, type)
        if (!source.staffInvisible) projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(source: Npc, target: Npc, spotanim: SpotanimType, type: String): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToNpc(source.bounds(), target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Npc,
        target: Npc,
        spotanim: SpotanimType,
        type: ProjAnimType,
    ): ProjAnim {
        val projAnim = ProjAnim.fromNpcToNpc(source, target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Npc,
        target: Player,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToPlayer(source.bounds(), target, spotanim.id, type)
        if (!target.staffInvisible) projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Npc,
        target: Player,
        spotanim: SpotanimType,
        type: ProjAnimType,
    ): ProjAnim {
        val projAnim = ProjAnim.fromNpcToPlayer(source, target, spotanim.id, type)
        if (!target.staffInvisible) projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Npc,
        target: CoordGrid,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToCoord(source.bounds(), target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Npc,
        target: CoordGrid,
        spotanim: SpotanimType,
        type: ProjAnimType,
    ): ProjAnim {
        val projAnim = ProjAnim.fromNpcToCoord(source, target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public companion object {
        private val EMPTY_UIDS = IntArray(0)

        /**
         * Combat area radius varies by sound family; 10 is the default, while duck hit sounds
         * use 5.
         */
        public const val COMBAT_SOUND_RADIUS: Int = 10
    }
}
