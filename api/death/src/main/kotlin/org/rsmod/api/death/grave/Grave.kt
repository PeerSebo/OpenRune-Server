package org.rsmod.api.death.grave

import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

/**
 * Grave visibility tokens select per-player child NPCs through varbit.gravestone_visible; use
 * token-specific children of the chosen cosmetic family.
 */
public enum class GraveVariant(public val varbitValue: Int, internal val tokenNpcPrefix: String) {
    Default(0, "npc.gravestone_default_"),
    Angel(1, "npc.gravestone_angel_");

    /**
     * The `npc.*` gameval name of the token child for [token] (`1..255`).
     *
     * Resolved on demand rather than pre-built into a table: a missing rscm symbol inside a static
     * initializer would poison the class for the lifetime of the JVM.
     */
    internal fun tokenNpcName(token: Int): String = tokenNpcPrefix + token.toString().padStart(3, '0')

    public companion object {
        public fun fromVarbitValue(value: Int): GraveVariant =
            entries.firstOrNull { it.varbitValue == value } ?: Default
    }
}

/**
 * A live grave in the world.
 *
 * The authoritative *contents* live in the owner's `inv.gravestone` and the authoritative *state*
 * (token, remaining ticks, variant) lives in `varp.gravestone_varp`; both persist across logout.
 * This record only tracks the things that cannot persist - the spawned [npc] and the world-unique
 * [token] it was spawned as.
 */
public class Grave
internal constructor(
    public val characterId: Int,
    /** `1..255`; the value written to `varbit.gravestone_visible` for the owner. */
    public val token: Int,
    public val coords: CoordGrid,
    public val variant: GraveVariant,
    internal var npc: Npc?,
) {
    override fun toString(): String =
        "Grave(characterId=$characterId, token=$token, coords=$coords, variant=$variant)"
}

public enum class GraveCollapseReason {
    Expired,

    ReplacedByNewDeath,

    Manual,
}
