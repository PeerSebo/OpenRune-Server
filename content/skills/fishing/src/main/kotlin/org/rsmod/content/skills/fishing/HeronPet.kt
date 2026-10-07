package org.rsmod.content.skills.fishing

import dev.openrune.types.ItemServerType
import dev.openrune.types.enums.enum
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.repo.obj.ObjRepository

object HeronPet {
    const val PET_OBJ: String = "obj.skillpetfish"
    const val GREAT_BLUE_OBJ: String = "obj.skillpetfish_tempoross"

    private val baseChance: Map<String, Int> by lazy {
        enum<ItemServerType, Int>("heron_pet_chance")
            .backing
            .mapNotNull { (fish, base) -> base?.let { fish.internalName to it } }
            .toMap()
    }

    fun ProtectedAccess.rollHeron(fish: String, objRepo: ObjRepository) {
        val base = baseChance[fish] ?: return
        val chance = (base - player.statBase("stat.fishing") * 25).coerceAtLeast(1)
        if (random.of(chance) != 0) {
            return
        }
        if (invAddOrDrop(objRepo, PET_OBJ, count = 1)) {
            spam("You have a funny feeling like you're being followed.")
        } else {
            spam("You feel something weird sneaking into your backpack.")
        }
    }
}
