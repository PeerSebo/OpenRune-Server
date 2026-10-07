package org.rsmod.content.other.commandbrowser

import dev.or2.central.account.Rights
import org.rsmod.game.cheat.CheatCommandMap

internal object CommandCatalog {
    const val PAGE_SIZE = 6

    data class Entry(val command: String, val description: String, val requiredRights: Rights?) {
        val privilege: String
            get() = privilegeLabel(requiredRights)
    }

    data class Page(val entries: List<Entry>, val index: Int, val pages: Int, val total: Int)

    fun page(registry: CheatCommandMap, filter: String, requestedPage: Int): Page {
        val query = filter.trim().removePrefix("::")
        val all = registry.commands.entries
            .asSequence()
            .filter { (name, handler) ->
                query.isEmpty() || name.contains(query, ignoreCase = true) ||
                    handler.desc.orEmpty().contains(query, ignoreCase = true) ||
                    privilegeLabel(handler.requiredRights).contains(query, ignoreCase = true)
            }
            .sortedBy { it.key }
            .map { (name, handler) -> Entry(name, handler.desc.orEmpty(), handler.requiredRights) }
            .toList()
        val pages = maxOf(1, (all.size + PAGE_SIZE - 1) / PAGE_SIZE)
        val index = requestedPage.coerceIn(0, pages - 1)
        return Page(all.drop(index * PAGE_SIZE).take(PAGE_SIZE), index, pages, all.size)
    }

    fun privilegeLabel(rights: Rights?): String = when (rights) {
        null, Rights.NONE -> "Player"
        Rights.MOD -> "Moderator"
        Rights.ADMINISTRATOR -> "Administrator"
    }

    /** CS2 text tags must never be interpreted inside command descriptions or user filters. */
    fun plainText(text: String): String = text.replace('<', '(').replace('>', ')')
}
