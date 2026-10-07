package dev.openrune.types

public enum class MoveRestrict(public val id: Int) {
    Normal(0),
    Blocked(1),

    /** Walks on either normal terrain, or blocked terrain. */
    BlockedNormal(2),
    Indoors(3),

    /** Walks only outside of buildings, or squares without roof collision flag. */
    Outdoors(4),

    /** Doesn't walk. */
    NoMove(5),

    /** Can walk through players or NPCs that'd normally block NPCs. */
    PassThru(6),
}
