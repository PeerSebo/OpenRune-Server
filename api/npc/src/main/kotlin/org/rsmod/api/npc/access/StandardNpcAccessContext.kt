package org.rsmod.api.npc.access

import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.processor.NpcHitProcessor
import org.rsmod.api.random.GameRandom

public data class StandardNpcAccessContext(
    private val getRandom: () -> GameRandom,
    private val getHitModifier: () -> NpcHitModifier,
    private val getHitProcessor: () -> NpcHitProcessor,
) {
    public val random: GameRandom by lazyLoad { getRandom() }
    public val hitModifier: NpcHitModifier by lazyLoad { getHitModifier() }
    public val hitProcessor: NpcHitProcessor by lazyLoad { getHitProcessor() }
}

private fun <T> lazyLoad(init: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, init)
