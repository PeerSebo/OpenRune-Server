package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import org.rsmod.api.mechanics.stun.isStunActionBlocked
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onContentEquipObj
import org.rsmod.api.script.onContentUnequipObj
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.PickpocketNpc
import org.rsmod.content.skills.thieving.configs.ThievingNpcs
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BlackjackScript
@Inject
constructor(
    private val aiInteractions: AiPlayerInteractions,
    private val invisibleLvls: InvisibleLevels,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (target in ThievingNpcs.blackjackTargets) {
            for (npc in target.npcs) {
                onOpNpc4(npc) { lure(it.npc, target) }
                onOpNpc5(npc) { knockOut(it.npc, target) }
            }
        }

        // Unequip fires before equip, so swapping blackjacks leaves the final state enabled.
        onContentEquipObj(BLACKJACK_CONTENT) {
            setBlackjackVar(player, ThievingRates.BLACKJACK_MULTI_ENABLED)
        }
        onContentUnequipObj(BLACKJACK_CONTENT) { setBlackjackVar(player, DISABLED) }
        onPlayerLogin {
            val value =
                if (player.wieldingBlackjack()) ThievingRates.BLACKJACK_MULTI_ENABLED else DISABLED
            setBlackjackVar(player, value)
        }
    }

    private suspend fun ProtectedAccess.lure(npc: Npc, target: PickpocketNpc) {
        arriveDelay()

        if (!wieldingBlackjack()) {
            mes("You need to be wielding a blackjack to do that.")
            return
        }

        if (isStunActionBlocked) {
            return
        }

        npc.resetMode()
        npc.facePlayer(player)
        npc.walk(coords)
        spam("You lure the ${target.displayName} away.")

        delay(ThievingRates.BLACKJACK_ACTION_DELAY)
    }

    private suspend fun ProtectedAccess.knockOut(npc: Npc, target: PickpocketNpc) {
        arriveDelay()

        if (!wieldingBlackjack()) {
            mes("You need to be wielding a blackjack to do that.")
            return
        }

        if (isStunActionBlocked) {
            return
        }

        npc.resetMode()

        faceEntitySquare(npc)
        anim(ThievingRates.BLACKJACK_SWING_ANIM)
        soundSynth(ThievingRates.BLACKJACK_CRUSH_SOUND)

        val success =
            statRandom(
                stat = "stat.thieving",
                low = ThievingRates.BLACKJACK_KO_LOW,
                high = ThievingRates.BLACKJACK_KO_HIGH,
                invisibleLevels = invisibleLvls,
            )

        if (success) {
            npc.anim(ThievingRates.BLACKJACK_STUN_SEQ)
            npc.spotanim(ThievingRates.BLACKJACK_STUN_SPOTANIM, height = KO_SPOTANIM_HEIGHT)
            npc.delay(ThievingRates.BLACKJACK_KO_CYCLES)
            BlackjackState.grant(player, npc)
            spam("You smack the ${target.displayName} over the head and knock them out.")

            if (target.koXp > 0.0) {
                statAdvance("stat.thieving", target.koXp * xpMods.get(player, "stat.thieving"))
            }
        } else {
            spam("Your blow only glances off the ${target.displayName}.")
            aiInteractions.interactOp(npc, player, InteractionOp.Op2)
        }

        delay(ThievingRates.BLACKJACK_ACTION_DELAY)
    }

    private fun ProtectedAccess.wieldingBlackjack(): Boolean = player.wieldingBlackjack()

    private fun Player.wieldingBlackjack(): Boolean {
        val weapon = righthand ?: return false
        return ThievingNpcs.blackjacks.any(weapon::isType)
    }

    private fun setBlackjackVar(player: Player, value: Int) {
        // TODO: Respect The Feud's progression when the quest is implemented.
        VarPlayerIntMapSetter.set(player, ThievingRates.BLACKJACK_MULTI_VARBIT, value)
    }

    private companion object {
        private const val BLACKJACK_CONTENT = "content.blackjack"
        private const val KO_SPOTANIM_HEIGHT = 92
        private const val DISABLED = 0
    }
}
