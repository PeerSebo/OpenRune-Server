package org.rsmod.content.skills.magic.utility

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLocT
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.magic.utility.configs.UtilityMethod
import org.rsmod.content.skills.magic.utility.configs.UtilitySpellRule
import org.rsmod.content.skills.magic.utility.configs.UtilitySpellRules
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ChargeOrbScript @Inject constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (rule in UtilitySpellRules.all.filter { it.method == UtilityMethod.ChargeOrb }) {
            val spell = checkNotNull(spells.getObjSpell(rule.spell))
            onOpLocT(checkNotNull(rule.obelisk), spell.component) {
                cast(spell, rule, it.loc, player.coords)
            }
        }
        onPlayerQueueWithArgs<PendingOrb>(ORB_QUEUE) {
            val task = it.args
            val rule = UtilitySpellRules.all.single { row -> row.spell.id == task.spell }
            cast(checkNotNull(spells.getObjSpell(rule.spell)), rule, task.obelisk, task.origin, quiet = true)
        }
    }

    internal fun ProtectedAccess.cast(
        spell: MagicSpell,
        rule: UtilitySpellRule,
        obelisk: BoundLocInfo,
        origin: CoordGrid,
        quiet: Boolean = false,
    ) {
        if (actionDelay > mapClock || player.coords != origin || obelisk.id != rule.obelisk?.asRSCM() ||
            !canReachObelisk(player.coords, obelisk)) return
        val slot = inv.indexOfFirst { it.isType("obj.stafforb") }
        if (slot == -1) {
            if (!quiet) mes("You need an unpowered orb to cast this spell.")
            return
        }
        val output = checkNotNull(rule.orb)
        if (runes.attemptCast(player, spell).isFailure()) return
        check(invReplaceSlot(inv, slot, 1, output).success)
        faceLoc(obelisk)
        anim(rule.sequence)
        spotanim(rule.spotanim, height = 92)
        rule.sound?.let { soundSynth(it) }
        statAdvance("stat.magic", spell.castXp)
        actionDelay = mapClock + rule.cooldown
        publish(PlayerActionCompleteEvent(player, PlayerAction.CastUtility, spell.obj.internalName))
        weakQueue(ORB_QUEUE, rule.cooldown + 1, PendingOrb(rule.spell.id, obelisk, origin))
    }

    internal fun canReachObelisk(coords: CoordGrid, loc: BoundLocInfo): Boolean {
        if (coords.level != loc.level) return false
        val dx = maxOf(loc.x - coords.x, coords.x - (loc.x + loc.adjustedWidth - 1), 0)
        val dz = maxOf(loc.z - coords.z, coords.z - (loc.z + loc.adjustedLength - 1), 0)
        return dx + dz == 1
    }

    private data class PendingOrb(val spell: Int, val obelisk: BoundLocInfo, val origin: CoordGrid)

    private companion object {
        const val ORB_QUEUE = "queue.utility_orb_repeat"
    }
}
