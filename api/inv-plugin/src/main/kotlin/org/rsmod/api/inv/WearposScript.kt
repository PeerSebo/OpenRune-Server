package org.rsmod.api.inv

import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.output.MiscOutput.setPlayerOp
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.righthand
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.specials.SpecialAttackType
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/*
 * Note: The logic and execution order in this script are designed for emulation accuracy. While
 * this approach is not optimized for efficiency and includes redundant operations, it does not have
 * a measurable impact on real-world performance.
 */
public class WearposScript : PluginScript() {
    private var Player.specialType by enumVarp<SpecialAttackType>("varp.sa_attack")

    private val Player.weaponSpecialActive: Boolean
        get() = specialType == SpecialAttackType.Weapon

    private val Player.shieldSpecialActive: Boolean
        get() = specialType == SpecialAttackType.Shield

    override fun ScriptContext.startup() {
        onWearposChange { player.disableSpecialAttack(wearpos) }

        // Content-specific varps belong in content hooks: 454 is Treasure Trails, 491/3413
        // Runecrafting, 3158-3160 the buff bar; 728/518 remain unidentified.

        onWearposChange { player.sendSoundAndPlayerOp(objType) }
        onWearposChange { player.validateWeaponVars(wearpos) }
    }

    private fun Player.disableSpecialAttack(wearpos: Wearpos) {
        val disableWeaponSpec = wearpos == Wearpos.RightHand && weaponSpecialActive
        val disableShieldSpec = wearpos == Wearpos.LeftHand && shieldSpecialActive
        if (disableWeaponSpec || disableShieldSpec) {
            specialType = SpecialAttackType.None
        }
    }

    private fun Player.sendSoundAndPlayerOp(type: ItemServerType) {
        val sound = type.paramOrNull(params.equipment_sound)
        sound?.let(::soundSynth)

        val righthand = getOrNull(this.righthand)
        val playerOp5 = righthand?.paramOrNull(params.player_op5_text)
        setPlayerOp(this, slot = 5, op = playerOp5, priority = playerOp5 != null)
    }

    private fun Player.validateWeaponVars(wearpos: Wearpos) {
        if (wearpos != Wearpos.RightHand && wearpos != Wearpos.LeftHand) {
            return
        }

        val righthand = getOrNull(this.righthand)
        val isTwoHanded = righthand?.isTwoHanded() ?: false
        val isRighthand = wearpos == Wearpos.RightHand

        // These two conditions aren't the most logical, however, they are done this way for
        // emulation purposes. Clearing the special attack type is not even required at this
        // point, however it is done in the official game.
        val updateCombatVars = isRighthand || !isTwoHanded
        val clearPendingSpec = isRighthand && !isTwoHanded

        if (updateCombatVars) {
            resyncVar("varp.com_mode")
        }

        if (clearPendingSpec) {
            specialType = SpecialAttackType.None
        }

        if (updateCombatVars) {
            PlayerInterfaceUpdates.updateCombatLevel(this)
            PlayerInterfaceUpdates.updateCombatTab(this)
        }
    }

    private fun ItemServerType.isTwoHanded(): Boolean =
        wearpos2 == Wearpos.LeftHand.slot || wearpos3 == Wearpos.LeftHand.slot
}
