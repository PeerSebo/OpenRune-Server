package org.rsmod.content.raids.theatreofblood.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM

internal class TheatreHealingHitmark : CacheTask() {
    override fun init(cache: Cache) {
        val native = checkNotNull(cache.data(CONFIG_INDEX, HITMARK_ARCHIVE, "hitmark.heal".asRSCM()))
        check(native.last() == 0.toByte())
        // Native healing cannot replace four active damage splats; preserve its art and replace the oldest.
        val visible = native.copyOf(native.size + 2)
        visible[native.lastIndex] = COMPARISON_OPCODE.toByte()
        visible[native.size] = 0
        visible[native.size + 1] = 0
        cache.write(CONFIG_INDEX, HITMARK_ARCHIVE, "hitmark.theatre_heal".asRSCM(), visible, null)
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val HITMARK_ARCHIVE = 32
        const val COMPARISON_OPCODE = 12
    }
}
