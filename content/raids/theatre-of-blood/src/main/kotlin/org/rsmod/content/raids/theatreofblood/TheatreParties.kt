package org.rsmod.content.raids.theatreofblood

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

internal class TheatreParty(val members: MutableList<Player>) {
    val leader get() = members.first()
    var session: InstanceSession? = null
    var run: TheatreRun? = null
}

@Singleton
internal class TheatreParties @Inject constructor() {
    private val memberships = IdentityHashMap<Player, TheatreParty>()

    fun get(player: Player): TheatreParty? = memberships[player]

    fun create(player: Player): Boolean {
        if (get(player) != null) {
            player.mes("You already belong to a Theatre party.")
            return false
        }
        memberships[player] = TheatreParty(mutableListOf(player))
        player.mes("You have formed a Normal Theatre party. Enter the Theatre through the entrance.")
        return true
    }

    fun join(player: Player, leader: String): Boolean {
        if (get(player) != null) {
            player.mes("Leave your current Theatre party before joining another.")
            return false
        }
        val party = memberships.values.firstOrNull {
            it.leader.displayName.equals(leader.trim(), true)
        }
        if (party == null) {
            player.mes("That player does not have an open Theatre party.")
            return false
        }
        if (party.run?.stage?.let { it != TheatreManager.LOBBY } == true) {
            player.mes("That Theatre party has already begun.")
            return false
        }
        if (party.members.size >= TheatreData.rules.maxPlayers) {
            player.mes("That Theatre party is full.")
            return false
        }
        party.members += player
        memberships[player] = party
        player.mes("You have joined ${party.leader.displayName}'s Theatre party. Use the entrance when ready.")
        return true
    }

    fun remove(player: Player) {
        val party = memberships.remove(player) ?: return
        party.members.remove(player)
    }

    fun ended(session: InstanceSession) {
        val party = memberships.values.firstOrNull { it.session === session } ?: return
        party.members.toList().forEach(::remove)
    }
}
