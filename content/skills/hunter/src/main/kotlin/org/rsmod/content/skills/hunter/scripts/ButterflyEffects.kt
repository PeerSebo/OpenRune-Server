package org.rsmod.content.skills.hunter.scripts

import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statHeal
import org.rsmod.content.skills.hunter.configs.NetPrey
import org.rsmod.game.entity.Player

internal object ButterflyEffects {
    fun apply(player: Player, prey: NetPrey) = with(player) {
        when (prey.filledJar) {
            "obj.butterfly_jar_ruby" -> statBoost("stat.attack", 4, 15)
            "obj.butterfly_jar_glacialis" -> statBoost("stat.defence", 4, 15)
            "obj.butterfly_jar_warlock" -> statBoost("stat.strength", 4, 15)
            "obj.butterfly_jar_snowy" -> healReduced("stat.hitpoints", 15)
            "obj.butterfly_jar_moonmoth" -> healReduced("stat.prayer", 22)
            "obj.butterfly_jar_sunmoth" -> {
                for (skill in RESTORABLE_SKILLS) healReduced("stat.$skill", 6, 20)
                healReduced("stat.hitpoints", 8)
            }
            else -> Unit
        }
    }

    private fun Player.healReduced(skill: String, constant: Int, percent: Int = 0) {
        if (stat(skill) < statBase(skill)) statHeal(skill, constant, percent)
    }

    private val RESTORABLE_SKILLS = listOf("attack", "strength", "defence", "ranged", "magic", "cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting", "smithing", "mining", "herblore", "agility", "thieving", "slayer", "farming", "runecrafting", "hunter", "construction")
}
