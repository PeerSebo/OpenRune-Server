package org.rsmod.content.skills.smithing.util

import dev.openrune.types.ItemServerType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.smithingLvl

object SmithingUtils {

    suspend fun requireSmithingLevel(
        access: ProtectedAccess,
        level: Int,
        actionDescription: String,
    ): Boolean {
        if (access.player.smithingLvl < level) {
            access.mesbox("You need a Smithing level of at least $level to $actionDescription.")
            return false
        }
        return true
    }

    suspend fun requireSmithingAndCraftingLevel(
        access: ProtectedAccess,
        level: Int,
        actionDescription: String,
    ): Boolean {
        val player = access.player
        if (player.smithingLvl < level || player.craftingLvl < level) {
            access.mesbox(
                "You need both Smithing and Crafting level of at least $level to $actionDescription."
            )
            return false
        }
        return true
    }

    fun itemName(type: ItemServerType, fallback: String = "item"): String =
        type.name.ifBlank { fallback }

    fun prefixAn(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return "a"
        }
        return when (trimmed.first().lowercaseChar()) {
            'a',
            'e',
            'i',
            'o',
            'u' -> "an $trimmed"
            else -> "a $trimmed"
        }
    }

    fun countLiteral(count: Int): String =
        when (count) {
            1 -> "one"
            2 -> "two"
            else -> count.toString()
        }

    fun ProtectedAccess.hasHammer(): Boolean =
        inv.contains("obj.hammer") ||
            inv.contains("obj.imcando_hammer") ||
            inv.contains("obj.imcando_hammer_offhand") ||
            worn.contains("obj.imcando_hammer") ||
            worn.contains("obj.imcando_hammer_offhand")

    fun pluralize(name: String, count: Int): String {
        val lower = name.lowercase()
        if (count == 1) {
            return lower
        }
        return when {
            lower.endsWith("s") -> lower
            lower.endsWith("y") -> "${lower.dropLast(1)}ies"
            lower.endsWith("x") || lower.endsWith("ch") || lower.endsWith("sh") -> "${lower}es"
            else -> "${lower}s"
        }
    }
}
