package org.rsmod.content.quest.manager

import org.rsmod.game.entity.Player

interface QuestField<T> {
    val name: String

    fun getOrNull(player: Player): T?

    fun clear(player: Player)

    fun migrate(player: Player) {}

    fun assertResolves() {}
}
