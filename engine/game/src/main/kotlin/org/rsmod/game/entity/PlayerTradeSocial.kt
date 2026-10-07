package org.rsmod.game.entity

public data class PlayerTradeSocial(
    public val filter: Int,
    public val friends: Set<String>,
    public val ignores: Set<String>,
) {
    public fun accepts(name: String): Boolean {
        val key = name.lowercase().replace('_', ' ').replace('\u00a0', ' ').trim()
        return key !in ignores && (filter == 0 || (filter == 1 && key in friends))
    }
}
