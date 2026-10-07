package org.rsmod.content.bosses.hespori

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathItemStorageHook
import org.rsmod.api.death.PlayerDeathRedirectHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Singleton
internal class HesporiReclaim @Inject constructor(private val garden: HesporiGarden) :
    PlayerDeathItemStorageHook, PlayerDeathCleanupHook, PlayerDeathRedirectHook {
    override fun destination(player: Player): CoordGrid? =
        HesporiGarden.EXIT.takeIf { garden.garden(player)?.contains(player.coords) == true }

    override fun store(player: Player, items: List<InvObj>): Boolean {
        if (garden.garden(player)?.contains(player.coords) != true) return false
        // Entry is refused until all old items are collected, avoiding overwrite on a second death.
        val stored = decode(player.attr[ITEMS]) + items
        player.attr[ITEMS] = encode(stored)
        player.hesporiReclaimPaid = false
        player.mes("Arno has retrieved your belongings. Speak to him outside the Hespori cave to reclaim them.")
        return true
    }

    override fun cleanup(player: Player) { garden.cleanup(player) }
    fun hasItems(player: Player): Boolean = !player.attr[ITEMS].isNullOrEmpty()

    suspend fun talk(access: ProtectedAccess) = with(access) {
        if (!hasItems(player)) {
            mes("Arno explains that a fully grown Hespori is dangerous. Plant a seed in the cave, then return with a spade and combat equipment.")
            val receiving = player.vars["varbit.farmguild_hespori_seeds"] == 0
            val option = if (receiving) "Stop receiving Hespori seeds." else "Start receiving Hespori seeds."
            if (choice2(option, 1, "Keep my current preference.", 0) == 1) {
                VarPlayerIntMapSetter.toggle(player, "varbit.farmguild_hespori_seeds")
                mes(if (receiving) "You will no longer find Hespori seeds while Farming." else "You can now find Hespori seeds while Farming.")
            }
            return@with
        }
        if (!player.hesporiReclaimPaid) {
            if (choice2("Pay Arno 25,000 coins to reclaim my items.", 1, "Leave them for now.", 0) != 1) return@with
            if (invTotal(inv, "obj.coins") < FEE) { mes("You need 25,000 coins to reclaim your belongings."); return@with }
            if (!invDel(inv, "obj.coins", FEE).success) return@with
            player.hesporiReclaimPaid = true
        }
        withdraw(this)
    }

    internal fun withdraw(access: ProtectedAccess) = with(access) {
        if (!player.hesporiReclaimPaid) return@with
        val remaining = decode(player.attr[ITEMS]).toMutableList()
        while (remaining.isNotEmpty()) {
            val item = remaining.first()
            if (!invAdd(inv, item).success) break
            remaining.removeAt(0)
            // Persist only what remains after a successful inventory transaction, with no suspension.
            player.attr[ITEMS] = encode(remaining)
        }
        if (remaining.isEmpty()) {
            player.attr.remove(ITEMS)
            player.hesporiReclaimPaid = false
            mes("Arno returns all of your belongings.")
        } else mes("Make some inventory space and speak to Arno again. You have already paid.")
    }

    companion object {
        val ITEMS = AttributeKey<String>("hespori_reclaim_items")
        const val FEE = 25_000
        fun encode(items: List<InvObj>): String = items.joinToString(";") { "${it.id},${it.count},${it.vars}" }
        fun decode(text: String?): List<InvObj> = if (text.isNullOrEmpty()) emptyList() else text.split(';').map { entry ->
            val fields = entry.split(',').map(String::toInt)
            val type = checkNotNull(ServerCacheManager.getItem(fields[0])) { "Unknown stored Hespori item ${fields[0]}" }
            InvObj(type, fields[1], fields[2])
        }
    }
}
