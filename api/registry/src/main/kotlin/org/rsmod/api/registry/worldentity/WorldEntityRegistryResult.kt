package org.rsmod.api.registry.worldentity

import kotlin.contracts.contract
import org.rsmod.game.entity.WorldEntity

public fun WorldEntityRegistryResult.Add.isSuccess(): Boolean {
    contract { returns(true) implies (this@isSuccess is WorldEntityRegistryResult.Add.Success) }
    return this is WorldEntityRegistryResult.Add.Success
}

public fun WorldEntityRegistryResult.Delete.isSuccess(): Boolean {
    contract { returns(true) implies (this@isSuccess is WorldEntityRegistryResult.Delete.Success) }
    return this is WorldEntityRegistryResult.Delete.Success
}

public class WorldEntityRegistryResult {
    public sealed class Add {
        public data object Success : Add()

        public sealed class Failure : Add()

        public data object NoAvailableSlot : Failure()

        /**
         * Zone sizes are wire-encoded as four bits each in `WorldEntityInfo.processLowResolution`,
         * and `RebuildWorldEntityV4` separately requires `0..<13`. Anything outside
         * `1..WorldEntity.MAX_ZONE_SIZE` is rejected up-front rather than silently corrupting the
         * packet.
         */
        public data class InvalidZoneSize(val sizeX: Int, val sizeZ: Int) : Failure()

        public data class InvalidRootCoords(val cause: String) : Failure()

        /**
         * rsprot's `WorldEntityMap.fillRectangle` throws when two avatars claim overlapping zones,
         * and `WorldEntityAvatarRepository.getOrAlloc` throws when an index is reused within a
         * cycle. Both surface here as a typed failure instead of escaping the game tick.
         */
        public data class AvatarAllocFailure(val cause: Throwable) : Failure()
    }

    public sealed class Delete {
        public data object Success : Delete()

        public sealed class Failure : Delete()

        public data object UnexpectedSlot : Failure()

        public data class ListSlotMismatch(val occupiedBy: WorldEntity?) : Failure()

        /**
         * Failed avatar deletion leaves the entity registered. Keep its deck pinned and retry;
         * freeing the slot would let two entities share one avatar.
         */
        public data class AvatarReleaseFailure(val cause: Throwable) : Failure()
    }
}
