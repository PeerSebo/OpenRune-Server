package org.rsmod.game.map

import kotlin.enums.enumEntries
import kotlin.math.absoluteValue
import kotlin.math.atan2
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds

public enum class Direction(public val id: Int, public val xOff: Int, public val zOff: Int) {
    NorthWest(0, -1, 1),
    North(1, 0, 1),
    NorthEast(2, 1, 1),
    West(3, -1, 0),
    East(4, 1, 0),
    SouthWest(5, -1, -1),
    South(6, 0, -1),
    SouthEast(7, 1, -1);

    public val angle: Int
        get() =
            when (this) {
                South -> 0
                SouthWest -> 256
                West -> 512
                NorthWest -> 768
                North -> 1024
                NorthEast -> 1280
                East -> 1536
                SouthEast -> 1792
            }

    public fun isCardinal(): Boolean = this in CARDINAL

    public fun isOrdinal(): Boolean = this in ORDINAL

    public operator fun component1(): Int = xOff

    public operator fun component2(): Int = zOff

    public companion object {
        public val CARDINAL: List<Direction> = listOf(South, North, West, East)

        public val ORDINAL: List<Direction> = listOf(SouthWest, NorthWest, SouthEast, NorthEast)

        private val ALL: List<Direction> = enumEntries()

        public operator fun get(id: Int): Direction? =
            when (id) {
                South.id -> South
                North.id -> North
                West.id -> West
                East.id -> East
                SouthWest.id -> SouthWest
                NorthWest.id -> NorthWest
                SouthEast.id -> SouthEast
                NorthEast.id -> NorthEast
                else -> null
            }

        /**
         * Angle from the centre of [a] to [b], in `0..2047`: South=0, West=512,
         * North=1024, East=1536. Uses the angular units of [Direction.angle].
         */
        public fun angleBetween(a: Bounds, b: Bounds): Int {
            val dx = a.fineCentreX - b.fineCentreX
            val dz = a.fineCentreZ - b.fineCentreZ
            return (atan2(dx, dz) * 325.94932345220167).toInt() and 2047
        }

        public fun between(a: Bounds, b: Bounds): Direction {
            return between(a, b, ALL)
        }

        public fun cardinalBetween(a: Bounds, b: Bounds): CardinalDirection {
            return when (val direction = between(a, b, CARDINAL)) {
                North -> CardinalDirection.North
                West -> CardinalDirection.West
                East -> CardinalDirection.East
                South -> CardinalDirection.South
                else -> error("Unhandled cardinal direction: $direction (a=$a, b=$b)")
            }
        }

        public fun ordinalBetween(a: Bounds, b: Bounds): OrdinalDirection {
            return when (val direction = between(a, b, ORDINAL)) {
                NorthWest -> OrdinalDirection.NorthWest
                NorthEast -> OrdinalDirection.NorthEast
                SouthWest -> OrdinalDirection.SouthWest
                SouthEast -> OrdinalDirection.SouthEast
                else -> error("Unhandled ordinal direction: $direction (a=$a, b=$b)")
            }
        }

        private fun between(a: Bounds, b: Bounds, search: Iterable<Direction>): Direction {
            require(a.level == b.level) { "Bounds must be on same level: a=$a, b=$b" }
            val angle = angleBetween(a, b)
            return search.minBy {
                val diff = angle - it.angle
                val signedDiff = ((diff + 1024) and 2047) - 1024
                signedDiff.absoluteValue
            }
        }
    }
}

public enum class CardinalDirection {
    South,
    North,
    West,
    East,
}

public enum class OrdinalDirection {
    SouthWest,
    NorthWest,
    SouthEast,
    NorthEast,
}

public fun CoordGrid.translate(direction: Direction, magnitude: Int = 1): CoordGrid =
    translate(direction.xOff * magnitude, direction.zOff * magnitude)
