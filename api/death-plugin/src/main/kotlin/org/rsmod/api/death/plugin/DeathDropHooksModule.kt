package org.rsmod.api.death.plugin

import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.api.death.PlayerDeathItemStorageHook
import org.rsmod.api.death.PlayerDeathRedirectHook
import org.rsmod.api.death.PlayerDeathSequenceHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPPlayerHitHook
import org.rsmod.api.death.PvPSkullHook
import org.rsmod.api.death.PvPSpecialAttackHook
import org.rsmod.api.death.grave.GraveCollapseHook
import org.rsmod.api.death.grave.GraveTimerPauseHook
import org.rsmod.plugin.module.PluginModule

public class DeathDropHooksModule : PluginModule() {
    override fun bind() {
        newSetBinding<NpcDeathDropHook>()
        newSetBinding<NpcDeathKillHook>()
        newSetBinding<PlayerDeathCleanupHook>()
        newSetBinding<PlayerDeathHook>()
        newSetBinding<PlayerDeathRedirectHook>()
        newSetBinding<PlayerDeathSequenceHook>()
        newSetBinding<PlayerDeathItemStorageHook>()
        newSetBinding<PlayerDeathItemHook>()
        newSetBinding<PlayerRespawnHook>()
        newSetBinding<PvPAttackValidateHook>()
        newSetBinding<PvPSkullHook>()
        newSetBinding<PvPPlayerHitHook>()
        newSetBinding<PvPSpecialAttackHook>()
        newSetBinding<GraveCollapseHook>()
        newSetBinding<GraveTimerPauseHook>()
        addSetBinding<PlayerDeathHook>(UimPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathHook>(StandardPvmDeathHook::class.java)
    }
}
