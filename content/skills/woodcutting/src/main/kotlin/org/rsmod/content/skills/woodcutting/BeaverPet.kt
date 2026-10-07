package org.rsmod.content.skills.woodcutting

import dev.openrune.types.ItemServerType
import dev.openrune.types.enums.enum
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.repo.obj.ObjRepository

// Missing enum entries skip the beaver roll; TODO: Verify remaining tree rates.
object BeaverPet {
    const val PET_OBJ: String = "obj.skillpetwc"

    private val baseChance: Map<String, Int> by lazy {
        enum<ItemServerType, Int>("beaver_pet_chance")
            .backing
            .mapNotNull { (log, base) -> base?.let { log.internalName to it } }
            .toMap()
    }

    fun ProtectedAccess.rollBeaver(log: String, objRepo: ObjRepository) {
        val base = baseChance[log] ?: return
        val chance = (base - player.statBase("stat.woodcutting") * 25).coerceAtLeast(1)
        if (random.of(chance) != 0) {
            return
        }
        if (invAddOrDrop(objRepo, PET_OBJ, count = 1)) {
            mes("You feel something weird sneaking into your backpack.")
        } else {
            // Custom fallback wording: do not claim the pet is following or in the backpack.
            mes("Your inventory is full, so your beaver pet appears on the ground beside you.")
        }
    }
}
