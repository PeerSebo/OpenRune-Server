@file:Suppress("konsist.avoid usage of stdlib Random in properties")

package org.rsmod.api.random

import kotlin.random.Random

public class DefaultGameRandom(private val random: Random) : GameRandom {
    public constructor(seed: Long) : this(Random(seed))

    public constructor() : this(Random)

    override fun of(maxExclusive: Int): Int = random.nextInt(maxExclusive)

    override fun of(minInclusive: Int, maxInclusive: Int): Int =
        random.nextInt(minInclusive, maxInclusive + 1)

    override fun randomDouble(): Double = random.nextDouble()
}
