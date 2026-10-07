@file:Suppress("UnusedImport")

package org.rsmod.game.region.zone

/**
 * Zone flags distinguish otherwise identical copied zones; include them in matching when
 * templates repeat a source zone.
 */
public enum class RegionZoneFlag(private val bit: Int) {
    Flag1(bit = 0),
    Flag2(bit = 1),
    Flag3(bit = 2),
    Flag4(bit = 3),
    Flag5(bit = 4),
    Flag6(bit = 5),
    Flag7(bit = 6),
    Flag8(bit = 7);

    public val bitmask: Int
        get() = 1 shl bit
}
