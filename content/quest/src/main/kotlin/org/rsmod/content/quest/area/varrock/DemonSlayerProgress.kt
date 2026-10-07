package org.rsmod.content.quest.area.varrock

import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Player

internal class DemonSlayerProgress(quest: Quest) {
    val traibornAsked = quest.flag(
        "TRAIBORN_ASKED", "varbit.demon_slayer_traiborn_asked", "varbit.demon_slayer_traiborn_asked_migrated",
    )
    val traibornBones = quest.int(
        "TRAIBORN_BONES", "varp.demon_slayer_traiborn_bones", "varbit.demon_slayer_traiborn_bones_migrated",
    )
    val traibornKeyGiven = quest.flag(
        "TRAIBORN_KEY_GIVEN", "varbit.demon_slayer_traiborn_key_given",
        "varbit.demon_slayer_traiborn_key_given_migrated",
    )
    val arisAsked = quest.int(
        "ARIS_ASKED", "varp.demon_slayer_aris_asked", "varbit.demon_slayer_aris_asked_migrated",
    )

    fun bonesOwed(player: Player): Int {
        if (!traibornAsked.get(player) || traibornKeyGiven.get(player)) return 0
        return (BonesRequired - traibornBones.get(player)).coerceAtLeast(0)
    }

    companion object {
        const val BonesRequired = 25
    }
}
