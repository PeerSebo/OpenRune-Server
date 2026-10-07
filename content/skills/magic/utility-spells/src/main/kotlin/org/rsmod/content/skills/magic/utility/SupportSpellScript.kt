package org.rsmod.content.skills.magic.utility

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.magicChargeUntil
import org.rsmod.api.combat.commons.magic.markOfDarknessUntil
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.restoreRunEnergyPercent
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.magic.utility.configs.UtilityMethod
import org.rsmod.content.skills.magic.utility.configs.UtilitySpellRule
import org.rsmod.content.skills.magic.utility.configs.UtilitySpellRules
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.RunEnergy
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SupportSpellScript @Inject constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript(), PlayerPostTickHook, PlayerDeathCleanupHook {
    private var Player.shadowUntil by intVarp("varp.magic_shadow_until")

    override fun ScriptContext.startup() {
        for (rule in UtilitySpellRules.all.filter { it.batch == 0 }) {
            val spell = checkNotNull(spells.getObjSpell(rule.spell))
            onIfOverlayButton(spell.component) { cast(spell, rule) }
        }
        onPlayerLogin { cleanup(player) }
        onPlayerLogout { cleanup(player) }
    }

    internal fun ProtectedAccess.cast(spell: MagicSpell, rule: UtilitySpellRule) {
        val cooldown = cooldownVarp(rule.method)
        if (player.vars[cooldown] > mapClock) return
        val prayer = player.stat("stat.prayer")
        val missingEnergy = (RunEnergy.MAX - player.runEnergy).coerceAtLeast(0)
        if (rule.method == UtilityMethod.VileVigour && (prayer <= 0 || missingEnergy == 0)) {
            mes(if (prayer <= 0) "You need Prayer points to cast Vile Vigour." else "Your run energy is already full.")
            return
        }
        if (rule.method == UtilityMethod.CureMe && !PlayerPoison.isPoisoned(player) && !PlayerVenom.isEnvenomed(player)) {
            mes("You are not poisoned.")
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) return
        VarPlayerIntMapSetter.set(player, cooldown, mapClock + rule.cooldown)
        when (rule.method) {
            UtilityMethod.CureMe -> {
                if (!PlayerVenom.reduceToPoison(player)) PlayerPoison.clear(player)
                mes(if (PlayerPoison.isPoisoned(player)) "The venom has been reduced to poison." else "You have been cured of poison.")
            }
            UtilityMethod.VileVigour -> {
                val points = minOf(prayer, (missingEnergy + RunEnergy.UNITS_PER_PERCENT - 1) / RunEnergy.UNITS_PER_PERCENT)
                statSub("stat.prayer", points, 0)
                player.restoreRunEnergyPercent(points)
            }
            UtilityMethod.ShadowVeil -> {
                player.shadowUntil = mapClock + player.statBase("stat.magic")
                VarPlayerIntMapSetter.set(player, "varbit.arceuus_shadow_veil_active", 1)
            }
            UtilityMethod.MarkOfDarkness -> {
                val multiplier = if (player.righthand.isType("obj.purging_staff")) 15 else 3
                player.markOfDarknessUntil = mapClock + player.statBase("stat.magic") * multiplier
                mes("You have placed a Mark of Darkness upon yourself.")
            }
            UtilityMethod.Charge -> {
                player.magicChargeUntil = mapClock + 700
                mes("<col=ff0000>You feel charged with magic power.</col>")
            }
            else -> error("Unsupported support spell: ${rule.method}")
        }
        anim(rule.sequence)
        spotanim(rule.spotanim, height = 0)
        rule.sound?.let { soundSynth(it) }
        statAdvance("stat.magic", spell.castXp)
    }

    override fun onPostTick(player: Player) {
        val now = player.currentMapClock
        if (player.shadowUntil > 0 && now >= player.shadowUntil) {
            player.shadowUntil = 0
            VarPlayerIntMapSetter.set(player, "varbit.arceuus_shadow_veil_active", 0)
        }
        if (player.magicChargeUntil > 0 && now >= player.magicChargeUntil) {
            player.magicChargeUntil = 0
            player.mes("<col=ff0000>Your magical charge fades away.</col>")
        }
        if (player.markOfDarknessUntil > 0) {
            if (now == player.markOfDarknessUntil - 10) player.mes("Your Mark of Darkness is about to run out.")
            if (now >= player.markOfDarknessUntil) {
                player.markOfDarknessUntil = 0
                player.spotanim("spotanim.mark_of_darkness_end_spotanim", height = 0)
                player.mes("Your Mark of Darkness has faded away.")
            }
        }
    }

    override fun cleanup(player: Player) {
        player.shadowUntil = 0
        player.magicChargeUntil = 0
        player.markOfDarknessUntil = 0
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_shadow_veil_active", 0)
        for (method in UtilitySpellRules.all.filter { it.batch == 0 }.map { it.method }) {
            VarPlayerIntMapSetter.set(player, cooldownVarp(method), 0)
        }
    }

    private fun cooldownVarp(method: UtilityMethod): String = when (method) {
        UtilityMethod.CureMe -> "varp.magic_cure_ready"
        UtilityMethod.VileVigour -> "varp.magic_vigour_ready"
        UtilityMethod.ShadowVeil -> "varp.magic_shadow_ready"
        UtilityMethod.MarkOfDarkness -> "varp.magic_mark_ready"
        UtilityMethod.Charge -> "varp.magic_charge_ready"
        else -> error("Unsupported support spell: $method")
    }
}
