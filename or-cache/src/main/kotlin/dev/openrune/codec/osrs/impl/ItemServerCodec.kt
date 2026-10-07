package dev.openrune.codec.osrs.impl

import dev.openrune.definition.opcode.*
import dev.openrune.definition.opcode.OpcodeType.BOOLEAN.enumType
import dev.openrune.definition.opcode.impl.DefinitionOpcodeEntityOps
import dev.openrune.definition.opcode.impl.DefinitionOpcodeListActions
import dev.openrune.definition.type.ItemType
import dev.openrune.definition.type.ObjStackability
import dev.openrune.types.ItemServerType
import dev.openrune.util.DefinitionOpcodeParamMap
import dev.openrune.util.WeaponCategory

class ItemServerCodec(
    val rev: Int,
    val items: Map<Int, ItemType>? = null,
    val custom: Map<Int, ItemServerType>? = emptyMap(),
) : OpcodeDefinitionCodec<ItemServerType>() {

    override val definitionCodec =
        OpcodeList<ItemServerType>().apply {
            add(DefinitionOpcode(2, OpcodeType.INT, ItemServerType::cost))
            add(DefinitionOpcode(4, OpcodeType.STRING, ItemServerType::name))
            add(DefinitionOpcode(7, OpcodeType.DOUBLE, ItemServerType::weight))
            add(DefinitionOpcode(8, OpcodeType.BOOLEAN, ItemServerType::stockmarket))
            add(DefinitionOpcode(9, OpcodeType.INT, ItemServerType::category))
            add(DefinitionOpcodeEntityOps(10, ItemServerType::options, rev))
            add(
                DefinitionOpcodeListActions(
                    11,
                    OpcodeType.STRING,
                    ItemServerType::interfaceOptions,
                    5,
                )
            )
            add(
                DefinitionOpcodeListActions(
                    12,
                    OpcodeType.STRING,
                    ItemServerType::interfaceOptions,
                    5,
                )
            )
            add(DefinitionOpcode(13, OpcodeType.INT, ItemServerType::certlink))
            add(DefinitionOpcode(14, OpcodeType.INT, ItemServerType::certtemplate))
            add(DefinitionOpcode(16, OpcodeType.INT, ItemServerType::placeholderLink))
            add(DefinitionOpcode(17, OpcodeType.INT, ItemServerType::placeholderTemplate))
            add(DefinitionOpcode(18, enumType<ObjStackability>(), ItemServerType::stacks))
            add(DefinitionOpcode(19, OpcodeType.INT, ItemServerType::wearpos1))
            add(DefinitionOpcode(20, OpcodeType.INT, ItemServerType::wearpos2))
            add(DefinitionOpcode(21, OpcodeType.INT, ItemServerType::wearpos3))
            add(DefinitionOpcodeParamMap(22, ItemServerType::paramsRaw, ItemServerType::paramMap))
            add(DefinitionOpcode(23, OpcodeType.STRING, ItemServerType::examine))

            add(
                DefinitionOpcode(
                    24,
                    decode = { buf, def, _ ->
                        val count = buf.readUnsignedByte().toInt()
                        val arr = IntArray(count)
                        repeat(count) { i -> arr[i] = buf.readInt() }
                        def.objvar = arr.toList()
                    },
                    encode = { buf, def ->
                        buf.writeByte(def.objvar.size)
                        for (v in def.objvar) {
                            buf.writeInt(v)
                        }
                    },
                    shouldEncode = { def -> def.objvar.isNotEmpty() },
                )
            )

            add(DefinitionOpcode(25, OpcodeType.INT, ItemServerType::playerCost))
            add(DefinitionOpcode(26, OpcodeType.INT, ItemServerType::playerCostDerived))
            add(DefinitionOpcode(27, OpcodeType.INT, ItemServerType::playerCostDerivedConst))
            add(DefinitionOpcode(28, OpcodeType.INT, ItemServerType::stockMarketBuyLimit))
            add(DefinitionOpcode(29, OpcodeType.INT, ItemServerType::stockMarketRecalcUsers))
            add(DefinitionOpcode(30, OpcodeType.BOOLEAN, ItemServerType::tradeable))
            add(DefinitionOpcode(31, OpcodeType.INT, ItemServerType::respawnRate))
            add(DefinitionOpcode(32, OpcodeType.INT, ItemServerType::dummyitem))
            add(DefinitionOpcode(33, OpcodeType.INT, ItemServerType::contentGroup))
            add(DefinitionOpcode(34, enumType<WeaponCategory>(), ItemServerType::weaponCategory))
            add(DefinitionOpcode(35, OpcodeType.INT, ItemServerType::transformlink))
            add(DefinitionOpcode(36, OpcodeType.INT, ItemServerType::transformtemplate))

            add(intListOpcode(37, ItemServerType::countCo))
            add(intListOpcode(38, ItemServerType::countObj))
        }

    private fun intListOpcode(
        opcode: Int,
        property: kotlin.reflect.KMutableProperty1<ItemServerType, MutableList<Int>?>,
    ) =
        DefinitionOpcode<ItemServerType>(
            opcode,
            decode = { buf, def, _ ->
                val count = buf.readUnsignedByte().toInt()
                val values = ArrayList<Int>(count)
                repeat(count) { values.add(buf.readInt()) }
                property.set(def, values)
            },
            encode = { buf, def ->
                val values = property.get(def).orEmpty()
                buf.writeByte(values.size)
                for (value in values) {
                    buf.writeInt(value)
                }
            },
            shouldEncode = { def -> !property.get(def).isNullOrEmpty() },
        )

    override fun ItemServerType.createData() {
        if (items == null) return
        val item = items[id] ?: return

        cost = item.cost
        name = item.name
        weight = item.weight
        stockmarket = item.stockMarket
        category = item.category
        options = item.options
        interfaceOptions = item.interfaceOptions
        certlink = item.noteLinkId
        certtemplate = item.noteTemplateId
        placeholderLink = item.placeholderLink
        placeholderTemplate = item.placeholderTemplate
        stacks = item.stacks
        wearpos2 = item.appearanceOverride1
        wearpos1 = item.equipSlot
        wearpos3 = item.appearanceOverride2
        // Unlike npcs and locs, item examine text *is* in the cache (item config opcode 3), so
        // there is no `.data/raw-cache/examines/items.csv` and none is needed: 16,768 of the
        // 16,770 named item ids already decode with text. `ItemType.examine` defaults to the
        // literal string "null" when opcode 3 is absent, which would otherwise be printed
        // verbatim in the examine box, so normalise it to empty.
        examine = item.examine.takeUnless { it.equals("null", ignoreCase = true) } ?: ""
        // Copy rather than alias: `customData` below merges into this map, and `item.params` is the
        // LIVE `ItemType`'s own map, shared with every other reader of the base definition.
        paramsRaw = item.params?.toMutableMap()
        countCo = item.countCo?.toMutableList()
        countObj = item.countObj?.toMutableList()

        val customData = custom?.get(id)

        if (customData != null) {
            // `[item.params]` blocks in `.data/raw-cache/server/items.toml` were being parsed and
            // then thrown away: nothing merged them into `paramsRaw`, so every packed item's param
            // map was byte-identical to LIVE (measured: 34,301 of 34,301). `NpcServerCodec` has
            // always done this merge; items never did. Measured against the current toml, all
            // 17,382 entries duplicate the LIVE value, so this changes no packed byte today - it
            // restores the override channel, which is the only way to author a param (a stat
            // requirement, an attack sound) that LIVE does not already carry.
            val customParams = customData.paramsRaw
            if (!customParams.isNullOrEmpty()) {
                val target = paramsRaw ?: mutableMapOf<Int, Any>().also { paramsRaw = it }
                target.putAll(customParams)
            }
            objvar = customData.objvar
            playerCost = customData.playerCost
            playerCostDerived = customData.playerCostDerived
            playerCostDerivedConst = customData.playerCostDerivedConst
            stockMarketBuyLimit = customData.stockMarketBuyLimit
            stockMarketRecalcUsers = customData.stockMarketRecalcUsers
            tradeable = customData.tradeable
            respawnRate = customData.respawnRate
            dummyitem = customData.dummyitem
            contentGroup = customData.contentGroup
            weaponCategory = customData.weaponCategory
            transformlink = customData.transformlink
            transformtemplate = customData.transformtemplate
        }

        certlink = normalize(certlink)
        certtemplate = normalize(certtemplate)
        placeholderLink = normalize(placeholderLink)
        placeholderTemplate = normalize(placeholderTemplate)
        transformlink = normalize(transformlink)
        transformtemplate = normalize(transformtemplate)
    }

    // Not sure why rsmod uses 0 and not -1 like osrs client but this is what it does
    fun normalize(value: Int): Int = if (value == -1) 0 else value

    override fun createDefinition(): ItemServerType = ItemServerType()
}
