package org.rsmod.game.obj

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

public class Obj(
    public val coords: CoordGrid,
    public var entity: ObjEntity,
    public val creationCycle: Int,
    public val receiverId: Long,
    public val ownerId: Long = NULL_OBSERVER_ID,
    public val vars: Int = 0,
) {
    public val x: Int
        get() = coords.x

    public val z: Int
        get() = coords.z

    public val level: Int
        get() = coords.level

    public val type: Int
        get() = entity.id

    public val count: Int
        get() = entity.count

    public val scope: ObjScope
        get() = ObjScope[entity.scope]

    public val isPrivate: Boolean
        get() = entity.scope == ObjScope.Private.id

    public val isPublic: Boolean
        get() = !isPrivate

    /**
     * The reserved receiver sentinel maps to null, even if a real observer UUID happens to
     * equal it.
     */
    public val nullableReceiverId: Long?
        get() = if (receiverId == NULL_OBSERVER_ID) null else receiverId

    /**
     * The reserved original-owner sentinel maps to null; ironman ownership checks must
     * distinguish it from a real player.
     */
    public val nullableOwnerId: Long?
        get() = if (ownerId == NULL_OBSERVER_ID) null else ownerId

    public fun reveal() {
        val entity = entity.copy(scope = ObjScope.Temp.id)
        this.entity = entity
    }

    public fun change(count: Int) {
        require(count > 0) { "`count` must be greater than 0. (count=$count)" }
        val entity = entity.copy(count = count)
        this.entity = entity
    }

    public fun isVisibleTo(player: Player): Boolean = isVisibleTo(player.observerUUID)

    public fun isVisibleTo(observer: Long?): Boolean = isPublic || observer == nullableReceiverId

    public fun isOriginalOwner(player: Player): Boolean = isOriginalOwner(player.observerUUID)

    public fun isOriginalOwner(observer: Long?): Boolean = observer == nullableOwnerId

    override fun toString(): String =
        "Obj(" +
            "coords=$coords, " +
            "type=$type, " +
            "count=$count, " +
            "scope=$scope, " +
            "creationCycle=$creationCycle, " +
            "receiverId=$nullableReceiverId" +
            ")"

    public companion object {
        public const val NULL_OBSERVER_ID: Long = Long.MIN_VALUE

        // TODO: Need to investigate certain interactions when it comes to ironman in pvp.
        //  - Can they pick up any of their death pile loot if the killer does not take it and
        //  it spawns publicly?

        public fun fromPvp(killer: Player, target: Player, obj: ItemServerType, count: Int): Obj {
            return fromPvp(killer, target, obj.id, count)
        }

        public fun fromPvp(killer: Player, target: Player, obj: InvObj): Obj {
            return fromPvp(killer, target, obj.id, obj.count, obj.vars)
        }

        private fun fromPvp(killer: Player, target: Player, obj: Int, count: Int, vars: Int = 0): Obj {
            val entity = ObjEntity(obj, count, ObjScope.Private.id)
            return Obj(
                coords = target.coords,
                entity = entity,
                creationCycle = killer.currentMapClock,
                receiverId = killer.observerId(),
                ownerId = target.observerId(),
                vars = vars,
            )
        }

        public fun fromPvn(killer: Player, target: Npc, obj: ItemServerType, count: Int): Obj {
            return fromPvn(killer, target.coords, obj.id, count)
        }

        public fun fromPvn(killer: Player, target: Npc, obj: InvObj): Obj {
            return fromPvn(killer, target.coords, obj.id, obj.count, obj.vars)
        }

        private fun fromPvn(killer: Player, coords: CoordGrid, obj: Int, count: Int, vars: Int = 0): Obj {
            val entity = ObjEntity(obj, count, ObjScope.Private.id)
            return Obj(
                coords = coords,
                entity = entity,
                creationCycle = killer.currentMapClock,
                receiverId = killer.observerId(),
                ownerId = killer.observerId(),
                vars = vars,
            )
        }

        public fun fromOwner(
            player: Player,
            coords: CoordGrid,
            obj: ItemServerType,
            count: Int,
        ): Obj {
            return fromOwner(player, coords, obj.id, count)
        }

        public fun fromOwner(
            player: Player,
            coords: CoordGrid,
            obj: String,
            count: Int,
        ): Obj {
            return fromOwner(player, coords, obj.asRSCM(RSCMType.OBJ), count)
        }

        public fun fromOwner(player: Player, coords: CoordGrid, obj: InvObj): Obj {
            return fromOwner(player, coords, obj.id, obj.count, obj.vars)
        }

        private fun fromOwner(player: Player, coords: CoordGrid, obj: Int, count: Int, vars: Int = 0): Obj {
            val entity = ObjEntity(obj, count, ObjScope.Private.id)
            return Obj(
                coords = coords,
                entity = entity,
                creationCycle = player.currentMapClock,
                receiverId = player.observerId(),
                ownerId = player.observerId(),
                vars = vars,
            )
        }

        public fun fromServer(
            clock: MapClock,
            coords: CoordGrid,
            obj: String,
            count: Int,
        ): Obj {
            return fromServer(clock, coords, obj.asRSCM(RSCMType.OBJ), count)
        }

        public fun fromServer(clock: MapClock, coords: CoordGrid, obj: InvObj): Obj {
            return fromServer(clock, coords, obj.id, obj.count, obj.vars)
        }

        private fun fromServer(clock: MapClock, coords: CoordGrid, obj: Int, count: Int, vars: Int = 0): Obj {
            val entity = ObjEntity(obj, count, ObjScope.Temp.id)
            return Obj(coords, entity, clock.cycle, NULL_OBSERVER_ID, vars = vars)
        }

        private fun Player.observerId(): Long {
            return observerUUID ?: error("`observerUUID` not set for player: $this")
        }
    }
}
