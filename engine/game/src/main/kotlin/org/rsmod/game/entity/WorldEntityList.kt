package org.rsmod.game.entity

/**
 * Index allocator for [WorldEntity].
 *
 * `slotPadding = 1` because `WorldEntityInfo.ROOT_WORLD` is index `0` and rsprot requires world
 * entity indices in `1..4095`. Capacity is 2048 rather than 4096 so every handed-out index also
 * satisfies `CamTargetV4.WorldEntityTarget`'s `0..<2048` requirement.
 */
public class WorldEntityList :
    EntityList<WorldEntity>(capacity = WorldEntity.MAX_INDEX + 1, slotPadding = 1)
