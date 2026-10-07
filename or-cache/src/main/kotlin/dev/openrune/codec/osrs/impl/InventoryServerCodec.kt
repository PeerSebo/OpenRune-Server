package dev.openrune.codec.osrs.impl

import dev.openrune.definition.opcode.DefinitionOpcode
import dev.openrune.definition.opcode.OpcodeDefinitionCodec
import dev.openrune.definition.opcode.OpcodeList
import dev.openrune.definition.opcode.OpcodeType
import dev.openrune.definition.opcode.OpcodeType.BOOLEAN.enumType
import dev.openrune.definition.type.InventoryType
import dev.openrune.types.InvScope
import dev.openrune.types.InvStackType
import dev.openrune.types.InvStock
import dev.openrune.types.InventoryServerType

class InventoryServerCodec(
    val types: Map<Int, InventoryType>? = null,
    val custom: Map<Int, InventoryServerType>? = emptyMap(),
) : OpcodeDefinitionCodec<InventoryServerType>() {

    override val definitionCodec =
        OpcodeList<InventoryServerType>().apply {
            add(DefinitionOpcode(1, OpcodeType.USHORT, InventoryServerType::size))
            add(DefinitionOpcode(2, OpcodeType.USHORT, InventoryServerType::flags))
            add(DefinitionOpcode(3, enumType<InvStackType>(), InventoryServerType::stack))
            add(DefinitionOpcode(4, enumType<InvScope>(), InventoryServerType::scope))
            add(
                DefinitionOpcode(
                    5,
                    decode = { buf, def, _ ->
                        def.stock =
                            List(buf.readUnsignedByte().toInt()) {
                                InvStock(
                                    buf.readInt(),
                                    buf.readUnsignedShort(),
                                    buf.readUnsignedShort().let { if (it == 65535) -1 else it },
                                )
                            }
                    },
                    encode = { buf, def ->
                        require(def.stock.size <= 255) { "Inventory ${def.id} has too many stock entries" }
                        buf.writeByte(def.stock.size)
                        def.stock.forEach {
                            require(it.count in 0..65535) { "Invalid stock count in inventory ${def.id}: $it" }
                            require(it.restockCycles in -1..65534) { "Invalid restock interval in inventory ${def.id}: $it" }
                            buf.writeInt(it.obj)
                            buf.writeShort(it.count)
                            buf.writeShort(it.restockCycles)
                        }
                    },
                )
            )
            add(DefinitionOpcode(6, OpcodeType.STRING, InventoryServerType::name))
            add(DefinitionOpcode(7, OpcodeType.INT, InventoryServerType::buyMultiplier))
            add(DefinitionOpcode(8, OpcodeType.INT, InventoryServerType::sellMultiplier))
            add(DefinitionOpcode(9, OpcodeType.INT, InventoryServerType::delta))
        }

    override fun InventoryServerType.createData() {
        if (types == null) return
        val inventoryType = types[id] ?: return
        size = inventoryType.size
        val customData = custom?.get(id)

        if (customData != null) {
            scope = customData.scope
            stack = customData.stack
            flags = customData.flags
            stock = customData.stock
            name = customData.name
            buyMultiplier = customData.buyMultiplier
            sellMultiplier = customData.sellMultiplier
            delta = customData.delta
        }
    }

    override fun createDefinition(): InventoryServerType = InventoryServerType()
}
