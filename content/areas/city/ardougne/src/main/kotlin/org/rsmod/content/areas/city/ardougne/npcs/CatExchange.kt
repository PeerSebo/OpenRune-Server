package org.rsmod.content.areas.city.ardougne.npcs

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.content.other.pets.cats.CatPet
import org.rsmod.content.other.pets.cats.CatPetManager
import org.rsmod.game.entity.Player

class CatExchange @Inject constructor(private val care: CatPetManager) {
    fun following(player: Player): CatPet? =
        care.following(player)?.let { care.followingPet(player) }

    fun sellFirstAvailable(player: Player): Boolean {
        val held = CatPet.all.firstOrNull { !it.isKitten && it.obj in player.inv }
        if (held != null) return sellHeld(player, held)
        if (following(player)?.isKitten != false) return false
        val sale = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            insert {
                into = inventory
                obj = "obj.deathrune".asRSCM()
                strictCount = runes(player)
            }
        }
        if (!sale.success) return false
        care.release(player)
        return true
    }

    fun sellHeld(player: Player, cat: CatPet): Boolean {
        if (cat.isKitten) return false
        return player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            delete { from = inventory; obj = cat.objId; strictCount = 1 }
            insert { into = inventory; obj = "obj.deathrune".asRSCM(); strictCount = runes(player) }
        }.success
    }

    fun runes(player: Player): Int =
        if (player.vars["varbit.ardougne_diary_easy_complete"] != 0) 200 else 100
}
