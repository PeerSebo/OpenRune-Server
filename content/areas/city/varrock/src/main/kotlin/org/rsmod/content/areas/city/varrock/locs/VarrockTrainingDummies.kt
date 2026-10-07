package org.rsmod.content.areas.city.varrock.locs

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseAttackLvl
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VarrockTrainingDummies
@Inject
constructor(
    private val attackManager: PlayerAttackManager,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.fai_varrock_sword_dummy") { hitDummy(it.loc) }
    }

    private suspend fun ProtectedAccess.hitDummy(dummy: BoundLocInfo) {
        if (attackManager.isAttackDelayed(player)) {
            return
        }
        attackManager.setNextAttackDelay(player, HIT_CADENCE)

        spam("You hit the dummy.")
        attackManager.playWeaponFx(player, currentMeleeSwing())
        locAnim(worldRepo, dummy, DUMMY_HIT_ANIM)

        delay(1)

        if (player.baseAttackLvl < ATTACK_LEVEL_CAP) {
            statAdvance(ATTACK_STAT, XP_PER_HIT)
        } else {
            mes("There is nothing more you can learn from hitting a dummy.")
        }
    }

    private fun ProtectedAccess.currentMeleeSwing(): CombatAttack.Melee =
        CombatAttack.Melee(
            weapon = player.worn[Wearpos.RightHand.slot],
            type = null,
            style = null,
            stance = CombatStance[vars["varp.com_mode"]] ?: CombatStance.Stance1,
        )

    private companion object {
        private const val DUMMY_HIT_ANIM = "seq.varrock_heavybag_defend"

        private const val ATTACK_STAT = "stat.attack"

        private const val HIT_CADENCE = 2

        private const val XP_PER_HIT = 5.0

        private const val ATTACK_LEVEL_CAP = 8
    }
}
