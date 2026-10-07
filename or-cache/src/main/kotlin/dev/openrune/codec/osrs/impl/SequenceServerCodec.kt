package dev.openrune.codec.osrs.impl

import dev.openrune.definition.opcode.DefinitionOpcode
import dev.openrune.definition.opcode.OpcodeDefinitionCodec
import dev.openrune.definition.opcode.OpcodeList
import dev.openrune.definition.opcode.OpcodeType
import dev.openrune.definition.type.SequenceType
import dev.openrune.types.SequenceServerType

class SequenceServerCodec(val sequences: Map<Int, SequenceType>? = null) :
    OpcodeDefinitionCodec<SequenceServerType>() {

    override val definitionCodec =
        OpcodeList<SequenceServerType>().apply {
            add(DefinitionOpcode(1, OpcodeType.USHORT, SequenceServerType::tickDuration))
            add(DefinitionOpcode(2, OpcodeType.USHORT, SequenceServerType::totalDelay))
            add(DefinitionOpcode(3, OpcodeType.BYTE, SequenceServerType::maxLoops))
            add(DefinitionOpcode(4, OpcodeType.BYTE, SequenceServerType::priority))
        }

    override fun SequenceServerType.createData() {
        if (sequences == null) return

        val seq = sequences[id] ?: return

        if (seq.skeletalId >= 0) {
            tickDuration = SequenceDuration.skeletalTicks(seq.rangeBegin, seq.rangeEnd)
            totalDelay = seq.rangeEnd - seq.rangeBegin
        } else {
            tickDuration = SequenceDuration.frameTicks(seq.frameDelays!!)
            totalDelay = seq.frameDelays!!.sum()
        }
        maxLoops = seq.maxLoops
        priority = seq.forcedPriority
    }

    override fun createDefinition(): SequenceServerType = SequenceServerType()
}
