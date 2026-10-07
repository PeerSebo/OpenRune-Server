package org.rsmod.content.raids.theatreofblood.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM

internal class TheatreExitOrator : CacheTask() {
    override fun init(cache: Cache) {
        val native = checkNotNull(cache.data(CONFIG_INDEX, NPC_ARCHIVE, "npc.tob_male_orator_vis".asRSCM()))
        check(native.last() == 0.toByte())
        val action = byteArrayOf(TALK_OPCODE.toByte()) + "Talk-to".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0)
        val visible = native.copyOf(native.size - 1) + action
        cache.write(CONFIG_INDEX, NPC_ARCHIVE, "npc.theatre_exit_orator".asRSCM(), visible, null)
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val NPC_ARCHIVE = 9
        const val TALK_OPCODE = 30
    }
}
