package org.rsmod.content.other.commands

import org.rsmod.api.player.output.mes
import org.rsmod.game.cheat.Cheat

internal fun Cheat.expectArgs(count: Int, usage: String): Boolean = expectArgs(count..count, usage)

internal fun Cheat.expectArgs(count: IntRange, usage: String): Boolean {
    if (args.size in count) return true
    player.mes("Usage: $usage")
    return false
}

internal fun String.debugLabel(): String = replace('<', '(').replace('>', ')').take(80)
