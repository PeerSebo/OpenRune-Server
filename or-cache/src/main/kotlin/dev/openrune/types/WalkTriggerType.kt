package dev.openrune.types

import dev.openrune.definition.Definition
import dev.openrune.toml.rsconfig.RsTableHeaders
import kotlin.contracts.contract

/**
 * Server-specific overwrite priority for walk triggers. This prevents minor triggers
 * such as emote resets from replacing movement restrictions such as freezing.
 */
public enum class WalkTriggerPriority(public val id: Int) {
    None(0),

    /** This walk trigger can only be overwritten by other [Low] or [High] priority triggers. */
    Low(1),

    /** This walk trigger **cannot** be overwritten, even by another [High] priority trigger. */
    High(2);

    public fun canOverwrite(other: WalkTriggerPriority?): Boolean =
        when (other) {
            null -> true
            High -> false
            Low -> this == Low || this == High
            None -> true
        }

    public companion object {
        public operator fun get(id: Int): WalkTriggerPriority? =
            when (id) {
                None.id -> None
                Low.id -> Low
                High.id -> High
                else -> null
            }
    }
}

@RsTableHeaders("walktrigger")
data class WalkTriggerType(
    override var id: Int = -1,
    var priority: WalkTriggerPriority = WalkTriggerPriority.None,
) : Definition {
    companion object {
        fun WalkTriggerType?.isType(type: WalkTriggerType): Boolean {
            contract { returns(true) implies (this@isType != null) }
            return this?.id == type.id
        }
    }
}
