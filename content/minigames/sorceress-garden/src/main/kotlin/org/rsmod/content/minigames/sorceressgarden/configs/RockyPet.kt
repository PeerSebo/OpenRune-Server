package org.rsmod.content.minigames.sorceressgarden.configs

import org.rsmod.api.player.protect.ProtectedAccess

object RockyPet {
    const val PET_OBJ: String = "obj.skillpetthieving"
    private const val BASE_CHANCE = 10339

    fun ProtectedAccess.rollRocky(season: GardenSeason) {
        val chance = (BASE_CHANCE - season.thievingLevel * 25).coerceAtLeast(1)
        if (random.of(chance) != 0 || inv.isFull()) {
            return
        }
        invAdd(inv, PET_OBJ, 1)
        spam("You have a funny feeling like you're being followed.")
    }
}
