package org.rsmod.content.quest.manager

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

class QuestAttribute<T>
internal constructor(
    override val name: String,
    val attributeKey: AttributeKey<T>,
    private val defaultProvider: () -> T,
) : QuestField<T> {
    fun default(): T = defaultProvider()

    fun get(player: Player): T {
        return player.attr.getOrPut(attributeKey) { defaultProvider() }
    }

    override fun getOrNull(player: Player): T? = player.attr[attributeKey]

    fun set(player: Player, value: T) {
        player.attr[attributeKey] = value
    }

    fun reset(player: Player): T {
        val value = defaultProvider()
        player.attr[attributeKey] = value
        return value
    }

    override fun clear(player: Player) {
        player.attr.remove(attributeKey)
    }

    fun exists(player: Player): Boolean = player.attr.has(attributeKey)
}
