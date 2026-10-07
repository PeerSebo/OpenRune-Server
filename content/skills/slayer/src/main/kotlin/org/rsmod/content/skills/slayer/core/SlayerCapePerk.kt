package org.rsmod.content.skills.slayer.core

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom

object SlayerCapePerk {

    private val slayerCapes = listOf("obj.skillcape_slayer", "obj.skillcape_slayer_trimmed")

    fun hasSlayerCape(access: ProtectedAccess): Boolean =
        slayerCapes.any { it in access.inv || it in access.worn }

    fun rollPerkProc(random: GameRandom): Boolean = random.randomBoolean(CAPE_PERK_DENOMINATOR)

    private const val CAPE_PERK_DENOMINATOR = 10
}
