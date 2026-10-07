package org.rsmod.api.mechanics.dig

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.map.CoordGrid

/**
 * Return false for ineligible sites so the next site or generic dig can run. Animation and
 * sound are already sent; handlers only produce the result.
 */
public typealias DigHandler = suspend ProtectedAccess.() -> Boolean

/**
 * Register sites here instead of adding another spade-op binding, which would replace the
 * shared handler.
 */
public object DigSites {
    /** The generic spade dig animation. Its opcode-7 right-hand item is `obj.spade` + 512. */
    public const val DIG_SEQ: String = "seq.human_dig"

    /**
     * `seq.human_dig` carries no baked sound, so the server sends this itself - the same pairing,
     * in the same order, that `FarmingScript` and `DraynorManorSearch` already use.
     */
    public const val DIG_SYNTH: String = "synth.DIGSPADE"

    /**
     * One tick releases the generic dig action; the looping client animation still completes
     * its swing.
     */
    public const val EMPTY_DIG_TICKS: Int = 1

    private val logger = InlineLogger()

    private val sites = mutableMapOf<CoordGrid, MutableList<DigSite>>()
    private val registeredKeys = mutableSetOf<String>()

    public fun register(key: String, coord: CoordGrid, handler: DigHandler) {
        register(key, listOf(coord), handler)
    }

    public fun register(key: String, coords: Iterable<CoordGrid>, handler: DigHandler) {
        require(registeredKeys.add(key)) { "Dig site '$key' is already registered." }
        val tiles = coords.toList()
        require(tiles.isNotEmpty()) { "Dig site '$key' registered with no coords." }
        for (coord in tiles) {
            sites.getOrPut(coord) { mutableListOf() } += DigSite(key, handler)
        }
    }

    public fun hasSite(coord: CoordGrid): Boolean = sites.containsKey(coord)

    internal suspend fun dispatch(access: ProtectedAccess): Boolean {
        val tile = access.player.coords
        val registered = sites[tile] ?: return false
        for (site in registered) {
            if (site.handler(access)) {
                logger.debug { "Dig site '${site.key}' claimed a dig at $tile." }
                return true
            }
        }
        return false
    }

    private class DigSite(val key: String, val handler: DigHandler)
}
