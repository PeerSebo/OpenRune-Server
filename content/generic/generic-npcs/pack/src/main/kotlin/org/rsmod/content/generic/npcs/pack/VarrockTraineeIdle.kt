package org.rsmod.content.generic.npcs.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.definition.codec.SequenceCodec
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import io.netty.buffer.Unpooled

internal class VarrockTraineeIdle : CacheTask() {
    override fun init(cache: Cache) {
        val codec = SequenceCodec(revision)
        val source = "seq.human_ready".asRSCM()
        val target = "seq.varrock_female_trainee_ready".asRSCM()
        val sequence = codec.loadData(target, checkNotNull(cache.data(CONFIG_INDEX, SEQUENCE_ARCHIVE, source)))
        val idle = sequence.copy(
            frameIDs = listOf(checkNotNull(sequence.frameIDs).first()),
            frameDelays = listOf(checkNotNull(sequence.frameDelays).first()),
            frameStep = 1,
        )
        val buffer = Unpooled.buffer()
        try {
            with(codec) { buffer.encode(idle) }
            if (sequence.verticalOffset != 0) {
                // The native codec decodes vertical offset but omits it when encoding.
                buffer.setByte(buffer.writerIndex() - 1, VERTICAL_OFFSET_OPCODE)
                buffer.writeByte(sequence.verticalOffset)
                buffer.writeByte(0)
            }
            val data = ByteArray(buffer.readableBytes())
            buffer.readBytes(data)
            cache.write(CONFIG_INDEX, SEQUENCE_ARCHIVE, target, data, null)
        } finally {
            buffer.release()
        }
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val SEQUENCE_ARCHIVE = 12
        const val VERTICAL_OFFSET_OPCODE = 16
    }
}
