package dev.openrune.codec.osrs.impl

import dev.openrune.definition.BuilderDefinitionCodec
import dev.openrune.definition.type.VarBitType
import dev.openrune.definition.type.builders.VarBitTypeBuilder
import dev.openrune.types.varp.VarpServerType
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

class VarBitServerTypeCodec(
    private val types: Map<Int, VarBitType>? = null,
    private val custom: Map<Int, VarBitType>? = null,
) : BuilderDefinitionCodec<VarBitType, VarBitTypeBuilder> {
    override fun builder(id: Int) = VarBitTypeBuilder(id)

    override fun build(builder: VarBitTypeBuilder): VarBitType {
        validateFields(builder.id, builder.varp, builder.startBit, builder.endBit)
        return builder.build()
    }

    override fun VarBitTypeBuilder.read(opcode: Int, buffer: ByteBuf) {
        require(opcode == 1) { "Unknown varbit opcode $opcode" }
        varp = buffer.readInt()
        startBit = buffer.readUnsignedByte().toInt()
        endBit = buffer.readUnsignedByte().toInt()
        validateFields(id, varp, startBit, endBit)
    }

    override fun ByteBuf.encode(definition: VarBitType) {
        val source = custom?.get(definition.id) ?: types?.get(definition.id) ?: definition
        validate(source)
        writeByte(1)
        writeInt(source.varp)
        writeByte(source.startBit)
        writeByte(source.endBit)
        writeByte(0)
    }

    override fun createDefinition() = VarBitType()

    fun encodeToBuffer(definition: VarBitType): ByteArray {
        val buffer = Unpooled.buffer()
        try {
            buffer.encode(definition)
            return ByteArray(buffer.readableBytes()).also { buffer.getBytes(buffer.readerIndex(), it) }
        } finally {
            buffer.release()
        }
    }

    companion object {
        private fun validate(definition: VarBitType) =
            validateFields(definition.id, definition.varp, definition.startBit, definition.endBit)

        private fun validateFields(id: Int, varp: Int, startBit: Int, endBit: Int) {
            require(varp >= 0) { "Varbit $id has a negative parent varp." }
            require(startBit in 0..31 && endBit in startBit..32 && endBit - startBit < 32) {
                "Varbit $id has invalid bit bounds $startBit..$endBit."
            }
        }

        fun validateParentTransmission(definition: VarBitType, parent: VarpServerType?) {
            if (definition.varp <= 65535) return
            require(parent != null && parent.id == definition.varp && parent.transmit.never) {
                "Varbit ${definition.id} requires a non-transmitted parent varp ${definition.varp}; " +
                    "the client protocol cannot represent this parent."
            }
        }
    }
}
