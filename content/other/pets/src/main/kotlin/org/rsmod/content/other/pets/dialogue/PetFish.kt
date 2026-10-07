package org.rsmod.content.other.pets.dialogue

import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.obj

internal fun rawFishingItems(): Set<String> =
    DbHelper.table("dbtable.fishing_catches")
        .mapTo(hashSetOf()) { it.obj("dbcol.fishing_catches:fish_item").internalName }
