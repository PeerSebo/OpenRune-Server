package org.rsmod.content.skills.farming.scripts

import org.rsmod.game.inv.InvObj
import org.rsmod.objtx.Transaction
import org.rsmod.objtx.TransactionInventory

internal fun Transaction<InvObj>.waterSeedling(
    inventory: TransactionInventory<InvObj>,
    seedling: Int,
    wateredSeedling: Int,
    wateringCan: Int,
    emptiedCan: Int,
) {
    delete { from = inventory; obj = seedling; strictCount = 1 }
    delete { from = inventory; obj = wateringCan; strictCount = 1 }
    insert { into = inventory; obj = wateredSeedling; strictCount = 1 }
    insert { into = inventory; obj = emptiedCan; strictCount = 1 }
}

internal fun Transaction<InvObj>.takeCompostProduct(
    inventory: TransactionInventory<InvObj>,
    product: Int,
    emptyBucket: Int?,
) {
    if (emptyBucket != null) {
        delete { from = inventory; obj = emptyBucket; strictCount = 1 }
    }
    insert { into = inventory; obj = product; strictCount = 1 }
}

internal fun Transaction<InvObj>.takeGardenerPayment(
    inventory: TransactionInventory<InvObj>,
    unnoted: Int,
    noted: Int,
    unnotedCount: Int,
    total: Int,
) {
    if (unnotedCount > 0) {
        delete { from = inventory; obj = unnoted; strictCount = unnotedCount }
    }
    val remainder = total - unnotedCount
    if (remainder > 0) {
        delete { from = inventory; obj = noted; strictCount = remainder }
    }
}
