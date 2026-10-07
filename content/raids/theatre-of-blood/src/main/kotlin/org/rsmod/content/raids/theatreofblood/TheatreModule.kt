package org.rsmod.content.raids.theatreofblood

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.*
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

class TheatreModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpecialAttackMap>(TheatreDawnbringer::class.java)
        addSetBinding<WeaponMap>(TheatreDawnbringer::class.java)
        addSetBinding<PlayerDeathSequenceHook>(TheatreDeathSequenceHook::class.java)
        addSetBinding<PlayerDeathItemStorageHook>(TheatreDeathStorageHook::class.java)
        addSetBinding<PlayerRestrictionHook>(TheatreRestrictions::class.java)
        addSetBinding<PlayerTeleportValidateHook>(TheatreRestrictions::class.java)
        addSetBinding<NpcAttackValidateHook>(TheatreRestrictions::class.java)
    }
}

internal class TheatreRestrictions @Inject constructor(private val manager: TheatreManager) :
    PlayerRestrictionHook, PlayerTeleportValidateHook, NpcAttackValidateHook {
    override fun restriction(player: Player, action: RestrictedAction): String? =
        if (manager.run(player) != null && player.vars[TheatreManager.DEAD] != 0)
            "Wait for your party to finish this room." else null

    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? =
        if (manager.run(player) != null && type != TeleportType.Exempt)
            "You cannot teleport out of the Theatre of Blood." else null

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val raid = manager.run(npc) ?: return NpcAttackValidateResult.Pass
        return if (!manager.active(raid) || player !in manager.players(raid)) {
            NpcAttackValidateResult.Deny("You cannot attack that from outside this encounter.")
        } else NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }
}
