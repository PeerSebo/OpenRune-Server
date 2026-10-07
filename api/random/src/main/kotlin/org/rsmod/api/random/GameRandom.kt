package org.rsmod.api.random

import org.rsmod.map.CoordGrid

// Konsist misparses the @Inject example when validating see tags.
@Suppress("konsist.test see tag references are formatted correctly")
/**
 * Injectable game randomness. Tests can substitute a deterministic implementation to reproduce
 * outcomes without changing gameplay code.
 *
 * ```
 * class MyPlugin @Inject constructor(private val random: GameRandom) : PluginScript() {
 *     fun rollDie(sides: Int): Int {
 *         return random.of(sides)
 *     }
 * }
 * ```
 *
 * @see [DefaultGameRandom]
 */
public interface GameRandom {
    /**
     * Returns a random [CoordGrid] translated by a random offset within the range `-radius` to
     * `radius` in both x and z directions, relative to the given [coords].
     *
     * @param coords The base coordinate to translate.
     * @param radius The maximum distance in both x and z directions from the base coordinate.
     * @return A new [CoordGrid] with the translated coordinates. The resulting coordinate may be
     *   the same as the input [coords].
     */
    public fun of(coords: CoordGrid, radius: Int): CoordGrid =
        coords.translate(xOffset = of(-radius, radius), zOffset = of(-radius, radius))

    public fun <T> pick(first: T, second: T): T =
        when (of(maxExclusive = 2)) {
            0 -> first
            1 -> second
            else -> throw IllegalStateException()
        }

    public fun <T> pick(first: T, second: T, third: T): T =
        when (of(maxExclusive = 3)) {
            0 -> first
            1 -> second
            2 -> third
            else -> throw IllegalStateException()
        }

    public fun <T> pick(first: T, second: T, third: T, fourth: T): T =
        when (of(maxExclusive = 4)) {
            0 -> first
            1 -> second
            2 -> third
            3 -> fourth
            else -> throw IllegalStateException()
        }

    public fun <T> pick(first: T, second: T, third: T, fourth: T, fifth: T): T =
        when (of(maxExclusive = 4)) {
            0 -> first
            1 -> second
            2 -> third
            3 -> fourth
            4 -> fifth
            else -> throw IllegalStateException()
        }

    public fun <T> pick(elements: Array<T>): T =
        pickOrNull(elements) ?: throw NoSuchElementException("Array is empty.")

    public fun <T> pickOrNull(elements: Array<T>): T? =
        if (elements.isEmpty()) null else elements[of(elements.size)]

    public fun <T> pick(elements: Collection<T>): T =
        pickOrNull(elements) ?: throw NoSuchElementException("Collection is empty.")

    public fun <T> pickOrNull(elements: Collection<T>): T? =
        if (elements.isEmpty()) null else elements.elementAt(of(elements.size))

    public fun of(rangeInclusive: IntRange): Int = of(rangeInclusive.first, rangeInclusive.last)

    public fun randomBoolean(maxExclusive: Int = 2): Boolean = of(maxExclusive) == 0

    public fun of(maxExclusive: Int): Int

    public fun of(minInclusive: Int, maxInclusive: Int): Int

    public fun randomDouble(): Double
}
