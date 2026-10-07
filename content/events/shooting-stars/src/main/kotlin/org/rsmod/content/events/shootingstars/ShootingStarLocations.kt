package org.rsmod.content.events.shootingstars

import org.rsmod.api.table.ShootingStarLocationsRow

fun ShootingStarLocationsRow.Companion.byKey(key: String): ShootingStarLocationsRow? =
    all().firstOrNull { it.key.equals(key, ignoreCase = true) }
