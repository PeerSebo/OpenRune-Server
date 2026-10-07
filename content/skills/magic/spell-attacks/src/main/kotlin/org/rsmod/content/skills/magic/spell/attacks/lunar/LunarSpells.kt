package org.rsmod.content.skills.magic.spell.attacks.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VengeanceScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val npcList: NpcList,
    private val playerList: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val vengeance =
            ServerCacheManager.getItem(VENGEANCE_OBJ.asRSCM(RSCMType.OBJ))?.let(spells::getObjSpell)
        if (vengeance != null) {
            onIfOverlayButton(vengeance.component) { castVengeance() }
        }
        onEvent<PlayerHitEvents.Impact> { onHit(player, hit) }
    }

    private suspend fun ProtectedAccess.castVengeance() {
        val spellObj = ServerCacheManager.getItem(VENGEANCE_OBJ.asRSCM(RSCMType.OBJ)) ?: return
        val spell = spells.getObjSpell(spellObj) ?: return
        if (!Vengeance.canCast(player, mapClock)) {
            mes("You can only cast vengeance spells every 30 seconds.")
            return
        }
        val result = runes.attemptCast(player, spell)
        if (result.isFailure()) {
            return
        }
        statAdvance("stat.magic", spell.castXp)
        anim(VENGEANCE_ANIM)
        spotanim(VENGEANCE_SPOT, height = VENGEANCE_SPOT_HEIGHT)
        Vengeance.arm(player, mapClock)
    }

    private fun onHit(player: Player, hit: Hit) {
        if (hit.damage <= 0 || !Vengeance.isArmed(player)) {
            return
        }
        if (hit.type == HitType.Typeless) {
            return
        }
        val reflected = (hit.damage * VENGEANCE_REFLECT_PERCENT) / 100
        if (reflected <= 0) {
            return
        }
        Vengeance.disarm(player)
        player.say("Taste vengeance!")
        when {
            hit.isFromNpc -> {
                val npc = hit.resolveNpcSource(npcList) ?: return
                npc.queueHit(
                    source = player,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = reflected,
                    modifier = NpcHitModifier {},
                )
            }
            hit.isFromPlayer -> {
                val attacker = hit.resolvePlayerSource(playerList) ?: return
                attacker.queueHit(
                    source = player,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = reflected,
                    modifier = NoopPlayerHitModifier,
                )
            }
        }
    }

    private companion object {
        private const val VENGEANCE_OBJ = "obj.94_vengeance"
        private const val VENGEANCE_ANIM = "seq.vengeance_spell_anim_nostalling"
        private const val VENGEANCE_SPOT = "spotanim.quest_lunar_spellbook_vengeance_spot_anim"
        private const val VENGEANCE_SPOT_HEIGHT = 0
        private const val VENGEANCE_REFLECT_PERCENT = 75
    }
}

class VengeanceOtherSpell : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register("obj.93_vengeance_other", VengeanceOther(manager))
    }

    private class VengeanceOther(private val manager: SpellAttackManager) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            manager.stopCombat(this)
            mes("You can only cast that spell on another player.")
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            manager.stopCombat(this)
            if (!Vengeance.canCast(target, mapClock)) {
                mes("That player has cast a vengeance spell too recently.")
                return
            }
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            anim("seq.vengeance_spell_anim_nostalling")
            target.spotanim("spotanim.quest_lunar_spellbook_vengeance_other_spot_anim")
            Vengeance.arm(target, mapClock)
            target.mes("${player.displayName} has cast Vengeance on you.")
        }
    }
}

internal object Vengeance {
    private var Player.armed by boolVarp("varp.vengeance_armed")
    private var Player.encodedLastCast by intVarp("varp.vengeance_last_cast")
    private const val COOLDOWN_TICKS = 50

    fun canCast(player: Player, mapClock: Int): Boolean {
        val last = player.encodedLastCast.takeUnless { it == 0 }?.minus(1) ?: return true
        return mapClock - last >= COOLDOWN_TICKS
    }

    fun arm(player: Player, mapClock: Int) {
        player.armed = true
        player.encodedLastCast = mapClock + 1
    }

    fun isArmed(player: Player): Boolean = player.armed

    fun disarm(player: Player) {
        player.armed = false
    }

    fun clearSession(player: Player) {
        disarm(player)
        player.encodedLastCast = 0
    }
}

class VengeanceStateScript : PluginScript(), PlayerDeathCleanupHook {
    override fun ScriptContext.startup() {
        onPlayerLogin { Vengeance.clearSession(player) }
        onPlayerLogout { Vengeance.clearSession(player) }
    }

    override fun cleanup(player: Player) {
        Vengeance.disarm(player)
    }
}
