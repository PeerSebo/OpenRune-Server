package org.rsmod.content.other.pets

import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.content.other.pets.cats.CatPetDeathHook
import org.rsmod.plugin.module.PluginModule

class PetsModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathCleanupHook>(CatPetDeathHook::class.java)
        addSetBinding<NpcDeathDropHook>(PetDropHook::class.java)
        addSetBinding<NpcDeathKillHook>(PetNpcDropHook::class.java)
    }
}
