package org.rsmod.content.other.pouches.lootingbag

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.util.UncheckedType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

object LootingBags {
    const val CLOSED = "obj.looting_bag"
    const val OPEN = "obj.looting_bag_open"
    const val INV = "inv.looting_bag"
    const val CAPACITY = 28

    val ALL = listOf(CLOSED, OPEN)

    val ids: Set<Int> by lazy { ALL.map { it.asRSCM(RSCMType.OBJ) }.toSet() }
    val openId: Int by lazy { OPEN.asRSCM(RSCMType.OBJ) }
    private val coinsId: Int by lazy { "obj.coins".asRSCM(RSCMType.OBJ) }

    fun rejection(obj: InvObj, type: ItemServerType): String? =
        when {
            obj.id in ids -> "You may be surprised to learn that bagception is not permitted."
            !type.tradeable || obj.id == coinsId -> "Only tradeable items can be put in the bag."
            else -> null
        }
}

// The cache inventory is temporary; mirror contents to a persistent attribute after each change.
val LOOTING_BAG_ATTR: AttributeKey<MutableList<Int>> = AttributeKey(persistenceKey = "looting_bag")

val Player.lootingBag: Inventory
    get() = invMap.getOrPut(LootingBags.INV)

fun Player.hasLootingBag(): Boolean = inv.any { it != null && it.id in LootingBags.ids }

fun Player.hasOpenLootingBag(): Boolean = inv.any { it != null && it.id == LootingBags.openId }

fun Player.saveLootingBag() {
    val encoded = LootingBagCodec.encode(lootingBag.objs)
    if (encoded.isEmpty()) {
        attr.remove(LOOTING_BAG_ATTR)
    } else {
        attr[LOOTING_BAG_ATTR] = encoded
    }
}

fun Player.restoreLootingBag() {
    val saved = attr[LOOTING_BAG_ATTR] ?: return
    val bag = lootingBag
    val decoded = LootingBagCodec.decode(saved, bag.size)
    for (slot in bag.indices) {
        bag[slot] = decoded[slot]
    }
}

object LootingBagCodec {
    fun encode(objs: Array<InvObj?>): MutableList<Int> {
        val encoded = ArrayList<Int>()
        for (obj in objs) {
            if (obj == null) {
                continue
            }
            encoded.add(obj.id)
            encoded.add(obj.count)
        }
        return encoded
    }

    @OptIn(UncheckedType::class)
    fun decode(encoded: List<Int>, size: Int): Array<InvObj?> {
        val objs = arrayOfNulls<InvObj>(size)
        var slot = 0
        var index = 0
        while (index + 1 < encoded.size && slot < size) {
            val id = encoded[index]
            val count = encoded[index + 1]
            index += 2
            if (id <= 0 || count <= 0) {
                continue
            }
            objs[slot++] = InvObj(id, count)
        }
        return objs
    }
}
