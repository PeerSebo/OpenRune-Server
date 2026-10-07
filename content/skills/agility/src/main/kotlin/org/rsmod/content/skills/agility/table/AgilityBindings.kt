package org.rsmod.content.skills.agility.table

import dev.openrune.types.ObjectServerType
import org.rsmod.api.player.events.interact.LocCategoryEvents
import org.rsmod.api.player.events.interact.LocContentEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.events.EventBus

internal const val SHARED_LADDER = "loc.laddertop_norim"

internal fun hasLocHandler(events: EventBus, type: ObjectServerType, option: Int): Boolean {
    if (hasLocHandler(events, type.id, option)) return true
    val content = type.contentGroup
    val category = type.category
    return when (option) {
        1 -> events.contains(LocContentEvents.Op1::class.java, content) ||
            events.contains(LocContentEvents.Ap1::class.java, content) ||
            events.contains(LocCategoryEvents.Op1::class.java, category) ||
            events.contains(LocCategoryEvents.Ap1::class.java, category)
        2 -> events.contains(LocContentEvents.Op2::class.java, content) ||
            events.contains(LocContentEvents.Ap2::class.java, content) ||
            events.contains(LocCategoryEvents.Op2::class.java, category) ||
            events.contains(LocCategoryEvents.Ap2::class.java, category)
        3 -> events.contains(LocContentEvents.Op3::class.java, content) ||
            events.contains(LocContentEvents.Ap3::class.java, content) ||
            events.contains(LocCategoryEvents.Op3::class.java, category) ||
            events.contains(LocCategoryEvents.Ap3::class.java, category)
        4 -> events.contains(LocContentEvents.Op4::class.java, content) ||
            events.contains(LocContentEvents.Ap4::class.java, content) ||
            events.contains(LocCategoryEvents.Op4::class.java, category) ||
            events.contains(LocCategoryEvents.Ap4::class.java, category)
        5 -> events.contains(LocContentEvents.Op5::class.java, content) ||
            events.contains(LocContentEvents.Ap5::class.java, content) ||
            events.contains(LocCategoryEvents.Op5::class.java, category) ||
            events.contains(LocCategoryEvents.Ap5::class.java, category)
        else -> error("Invalid object option: $option")
    }
}

internal fun hasLocHandler(events: EventBus, loc: Int, option: Int): Boolean =
    when (option) {
        1 -> events.contains(LocEvents.Op1::class.java, loc) || events.contains(LocEvents.Ap1::class.java, loc)
        2 -> events.contains(LocEvents.Op2::class.java, loc) || events.contains(LocEvents.Ap2::class.java, loc)
        3 -> events.contains(LocEvents.Op3::class.java, loc) || events.contains(LocEvents.Ap3::class.java, loc)
        4 -> events.contains(LocEvents.Op4::class.java, loc) || events.contains(LocEvents.Ap4::class.java, loc)
        5 -> events.contains(LocEvents.Op5::class.java, loc) || events.contains(LocEvents.Ap5::class.java, loc)
        else -> error("Invalid object option: $option")
    }
