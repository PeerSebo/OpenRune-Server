package org.rsmod.game.entity.worldentity

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.WorldEntity

public class WorldEntityStateEvents {
    public data class Create(val worldEntity: WorldEntity) : UnboundEvent

    public data class Delete(val worldEntity: WorldEntity) : UnboundEvent
}
