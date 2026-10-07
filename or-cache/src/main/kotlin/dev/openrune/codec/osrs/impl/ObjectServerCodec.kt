package dev.openrune.codec.osrs.impl

import dev.openrune.definition.opcode.DefinitionOpcode
import dev.openrune.definition.opcode.OpcodeDefinitionCodec
import dev.openrune.definition.opcode.OpcodeList
import dev.openrune.definition.opcode.OpcodeType
import dev.openrune.definition.opcode.impl.DefinitionOpcodeEntityOps
import dev.openrune.definition.opcode.impl.DefinitionOpcodeTransforms
import dev.openrune.definition.type.ObjectType
import dev.openrune.types.ObjectServerType
import dev.openrune.util.DefinitionOpcodeParamMap

class ObjectServerCodec(
    val rev: Int,
    val objects: Map<Int, ObjectType>? = null,
    val custom: Map<Int, ObjectServerType>? = emptyMap(),
    val examines: Map<Int, String> = emptyMap(),
) : OpcodeDefinitionCodec<ObjectServerType>() {

    override val definitionCodec =
        OpcodeList<ObjectServerType>().apply {
            add(DefinitionOpcode(1, OpcodeType.STRING, ObjectServerType::name))
            add(DefinitionOpcode(2, OpcodeType.STRING, ObjectServerType::desc))
            add(DefinitionOpcode(3, OpcodeType.INT, ObjectServerType::width))
            add(DefinitionOpcode(4, OpcodeType.INT, ObjectServerType::length))
            add(DefinitionOpcode(5, OpcodeType.INT, ObjectServerType::category))
            add(DefinitionOpcode(6, OpcodeType.USHORT, ObjectServerType::contentGroup))
            add(DefinitionOpcode(7, OpcodeType.USHORT, ObjectServerType::forceApproachFlags))
            add(DefinitionOpcode(8, OpcodeType.USHORT, ObjectServerType::blockWalk))
            add(DefinitionOpcode(9, OpcodeType.BOOLEAN, ObjectServerType::blockRange))
            add(DefinitionOpcode(10, OpcodeType.BOOLEAN, ObjectServerType::breakRouteFinding))

            add(DefinitionOpcodeEntityOps(11, ObjectServerType::actions, rev))
            add(
                DefinitionOpcodeTransforms(
                    IntRange(12, 13),
                    ObjectServerType::transforms,
                    ObjectServerType::multiVarBit,
                    ObjectServerType::multiVarp,
                    ObjectServerType::multiDefault,
                )
            )
            add(
                DefinitionOpcodeParamMap(
                    14,
                    ObjectServerType::paramsRaw,
                    ObjectServerType::paramMap,
                )
            )
        }

    override fun ObjectServerType.createData() {
        if (objects == null) return

        val obj = objects[id] ?: return

        name = obj.name
        category = obj.category
        width = obj.sizeX
        length = obj.sizeY
        blockRange = obj.impenetrable
        blockWalk = obj.solid
        forceApproachFlags = obj.clipMask
        breakRouteFinding = obj.isHollow
        multiVarBit = obj.multiVarBit
        multiVarp = obj.multiVarp
        transforms = obj.transforms?.toMutableList()
        multiDefault = obj.multiDefault

        paramsRaw = obj.params
        actions = obj.actions

        // Loc examine text is server-side only in OSRS - it is never present in the loc cache
        // config - so `.data/raw-cache/examines/locs.csv` is its only source. This assignment used
        // to live inside the `customData != null` branch below, which meant the CSV reached only
        // the ~800 locs that happen to carry a toml override for unrelated reasons; every other loc
        // packed with `desc=""` and examining it printed an empty line. Measured before the fix:
        // 793 of 62,431 packed locs had examine text while the CSV named 30,239 of them.
        desc = examines[id] ?: ""

        val customData = custom?.get(id)

        if (customData != null) {
            desc = customData.desc.takeIf { it.isNotEmpty() } ?: desc
            contentGroup = customData.contentGroup
            paramsRaw = (paramsRaw.orEmpty() + customData.paramsRaw.orEmpty()).toMutableMap()
        }
    }

    override fun createDefinition(): ObjectServerType = ObjectServerType()
}
