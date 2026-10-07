package org.rsmod.api.player.hook

public enum class TeleportType {
    /** Standard spell, tablet, or similar teleport - works up to level 20 Wilderness. */
    Standard,

    /** Member jewellery and items that work up to level 30 Wilderness. */
    MemberLevel30,

    /** Chronicle - cannot be used anywhere in the Wilderness. */
    Chronicle,

    /** Minigame teleports - cannot be used anywhere in the Wilderness. */
    Minigame,

    /** Wilderness levers ignore depth, while retaining teleport-block and area hooks. */
    Lever,

    /** Bypasses wilderness teleport restrictions (e.g. admin commands). */
    Exempt,
}
