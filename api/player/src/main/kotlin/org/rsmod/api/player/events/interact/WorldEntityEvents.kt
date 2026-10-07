package org.rsmod.api.player.events.interact

import org.rsmod.game.entity.WorldEntity

/**
 * Keyed on the world entity's **cache config id** (`WorldEntityType` id), not its avatar index, so
 * a script registers once for "any raft" rather than once per spawned boat.
 *
 * There are no `Ap` variants: a world entity is not a [org.rsmod.game.entity.PathingEntity], so
 * there is no route to it and no approach phase - a click resolves immediately, the way an
 * interface button does.
 */
public sealed class WorldEntityEvents {
    public sealed class Op(public val worldEntity: WorldEntity) :
        OpEvent(worldEntity.configId.toLong())

    public class Op1(worldEntity: WorldEntity) : Op(worldEntity)

    public class Op2(worldEntity: WorldEntity) : Op(worldEntity)

    public class Op3(worldEntity: WorldEntity) : Op(worldEntity)

    public class Op4(worldEntity: WorldEntity) : Op(worldEntity)

    public class Op5(worldEntity: WorldEntity) : Op(worldEntity)
}

public sealed class WorldEntityTEvents {
    public class Op(
        public val worldEntity: WorldEntity,
        public val selectedInterfaceId: Int,
        public val selectedComponentId: Int,
        public val selectedSub: Int,
        public val selectedObj: Int,
    ) : OpEvent(worldEntity.configId.toLong())
}
