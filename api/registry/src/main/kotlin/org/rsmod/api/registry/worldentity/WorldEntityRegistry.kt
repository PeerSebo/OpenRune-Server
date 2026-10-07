package org.rsmod.api.registry.worldentity

import jakarta.inject.Inject
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.worldentity.NoopWorldEntityInfo
import org.rsmod.game.entity.worldentity.WorldEntityStateEvents
import org.rsmod.map.CoordGrid

/**
 * Run on the game thread; protocol avatar allocation/release asserts it. Validate deck sizes
 * before exposing an avatar.
 */
public class WorldEntityRegistry
@Inject
constructor(private val worldEntityList: WorldEntityList, private val eventBus: EventBus) {
    private var liveCount = 0

    public fun count(): Int = liveCount

    public fun isEmpty(): Boolean = liveCount == 0

    public fun add(worldEntity: WorldEntity): WorldEntityRegistryResult.Add {
        val sizeX = worldEntity.zoneSizeX
        val sizeZ = worldEntity.zoneSizeZ
        val validSize =
            sizeX in 1..WorldEntity.MAX_ZONE_SIZE && sizeZ in 1..WorldEntity.MAX_ZONE_SIZE
        if (!validSize) {
            return WorldEntityRegistryResult.Add.InvalidZoneSize(sizeX, sizeZ)
        }

        val rootCoords = worldEntity.rootCoords
        if (rootCoords == CoordGrid.NULL) {
            return WorldEntityRegistryResult.Add.InvalidRootCoords("`rootCoords` was not assigned.")
        }

        // `WorldEntityAvatarFactory.alloc` requires min <= max and both in 0..<4.
        if (worldEntity.minLevel !in 0..3 || worldEntity.maxLevel !in 0..3) {
            return WorldEntityRegistryResult.Add.InvalidRootCoords(
                "Instance levels out of range: ${worldEntity.minLevel}..${worldEntity.maxLevel}"
            )
        }
        if (worldEntity.minLevel > worldEntity.maxLevel) {
            return WorldEntityRegistryResult.Add.InvalidRootCoords(
                "`minLevel` above `maxLevel`: ${worldEntity.minLevel}..${worldEntity.maxLevel}"
            )
        }

        val slot =
            worldEntityList.nextFreeSlot() ?: return WorldEntityRegistryResult.Add.NoAvailableSlot
        worldEntityList[slot] = worldEntity
        worldEntity.slotId = slot

        try {
            eventBus.publish(WorldEntityStateEvents.Create(worldEntity))
        } catch (cause: RuntimeException) {
            // Overlapping instance rectangles (`WorldEntityMap.fillRectangle`) and index reuse
            // within a cycle (`WorldEntityAvatarRepository.getOrAlloc`) both throw. Roll the slot
            // back so a failed spawn does not leak an index or leave a half-registered entity.
            worldEntityList.remove(slot)
            worldEntity.slotId = WorldEntity.INVALID_SLOT
            worldEntity.infoProtocol = NoopWorldEntityInfo
            return WorldEntityRegistryResult.Add.AvatarAllocFailure(cause)
        }

        liveCount++
        return WorldEntityRegistryResult.Add.Success
    }

    public fun del(worldEntity: WorldEntity): WorldEntityRegistryResult.Delete {
        val slot = worldEntity.slotId
        if (slot == WorldEntity.INVALID_SLOT) {
            return WorldEntityRegistryResult.Delete.UnexpectedSlot
        } else if (worldEntityList[slot] != worldEntity) {
            return WorldEntityRegistryResult.Delete.ListSlotMismatch(worldEntityList[slot])
        }
        worldEntityList.remove(slot)
        liveCount--
        try {
            eventBus.publish(WorldEntityStateEvents.Delete(worldEntity))
        } catch (cause: RuntimeException) {
            worldEntityList[slot] = worldEntity
            liveCount++
            return WorldEntityRegistryResult.Delete.AvatarReleaseFailure(cause)
        }
        worldEntity.slotId = WorldEntity.INVALID_SLOT
        return WorldEntityRegistryResult.Delete.Success
    }

    public operator fun get(index: Int): WorldEntity? {
        if (index <= 0 || index > WorldEntity.MAX_INDEX) {
            return null
        }
        return worldEntityList[index]
    }

    public fun findByInstanceCoords(coords: CoordGrid): WorldEntity? {
        if (liveCount == 0 || !RegionRegistry.inWorkingArea(coords)) {
            return null
        }
        for (worldEntity in worldEntityList) {
            if (coords in worldEntity) {
                return worldEntity
            }
        }
        return null
    }

    public fun forEach(action: (WorldEntity) -> Unit) {
        if (liveCount == 0) {
            return
        }
        for (worldEntity in worldEntityList) {
            action(worldEntity)
        }
    }
}
