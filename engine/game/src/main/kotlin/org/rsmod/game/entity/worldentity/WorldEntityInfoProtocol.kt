package org.rsmod.game.entity.worldentity

public interface WorldEntityInfoProtocol {
    /**
     * @param level the projected **root-world** level the entity renders on.
     * @param fineX absolute root-world x in fine units (tile * 128).
     * @param fineZ absolute root-world z in fine units (tile * 128).
     * @param teleport when `true` the client jumps instead of interpolating.
     */
    public fun updateCoord(level: Int, fineX: Int, fineZ: Int, teleport: Boolean)

    /** @param angle 0..2047, where 2048 units is a full turn. */
    public fun updateAngle(angle: Int)

    public fun setSequence(seq: Int, delay: Int)

    /** @param packed bit flag; only the 5 lowest bits are used, one per option index. */
    public fun toggleOps(packed: Byte)

    /** When `true`, the entity only renders for its owner and for players currently aboard it. */
    public fun setSpecific(specific: Boolean)

    public fun isActive(): Boolean
}

public data object NoopWorldEntityInfo : WorldEntityInfoProtocol {
    override fun updateCoord(level: Int, fineX: Int, fineZ: Int, teleport: Boolean) {}

    override fun updateAngle(angle: Int) {}

    override fun setSequence(seq: Int, delay: Int) {}

    override fun toggleOps(packed: Byte) {}

    override fun setSpecific(specific: Boolean) {}

    override fun isActive(): Boolean = false
}
