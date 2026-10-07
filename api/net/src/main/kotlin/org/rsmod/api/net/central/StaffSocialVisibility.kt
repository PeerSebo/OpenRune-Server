package org.rsmod.api.net.central

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale
import java.util.WeakHashMap
import net.rsprot.protocol.game.outgoing.social.UpdateFriendList
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.player.StaffVisibilityChanged
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
public class StaffSocialVisibility @Inject constructor(private val players: PlayerList) {
    // Only entries already sent to this recipient are retained. Values contain no Player reference.
    private val known = WeakHashMap<Player, MutableMap<String, UpdateFriendList.Friend>>()

    public fun snapshot(owner: Player, friends: List<UpdateFriendList.Friend>): List<UpdateFriendList.Friend> {
        known[owner] = friends.associateByTo(linkedMapOf()) { key(it.name) }
        return friends.map { mask(owner, it) }
    }

    public fun presence(owner: Player, friend: UpdateFriendList.Friend): UpdateFriendList.Friend {
        val entries = known.getOrPut(owner) { linkedMapOf() }
        friend.previousName?.takeIf { it.isNotBlank() && key(it) != key(friend.name) }?.let { entries.remove(key(it)) }
        entries[key(friend.name)] = friend
        return mask(owner, friend)
    }

    public fun forget(owner: Player, name: String) {
        val normalized = key(name)
        known[owner]?.entries?.removeIf {
            it.key == normalized || it.value.previousName?.let(::key) == normalized
        }
    }

    public fun clear(owner: Player) {
        known.remove(owner)
    }

    /** Called on the game thread after the toggle; unknown recipients never receive the name. */
    public fun refresh(subject: Player) {
        for (owner in players) {
            if (owner === subject || owner.loggingOut) continue
            val friends = known[owner]?.values ?: continue
            val changed = friends.filter { matches(subject, it.name) }.map { mask(owner, it) }
            if (changed.isNotEmpty()) owner.client.write(UpdateFriendList(changed))
        }
    }

    private fun mask(owner: Player, friend: UpdateFriendList.Friend): UpdateFriendList.Friend {
        if (friend.worldId <= 0 || players.none {
            it !== owner && it.staffInvisible && matches(it, friend.name)
        }) return friend
        return UpdateFriendList.OfflineFriend(
            added = friend.added, name = friend.name, previousName = friend.previousName,
            rank = friend.rank, properties = friend.properties, notes = friend.notes,
        )
    }

    private fun matches(player: Player, name: String): Boolean {
        val normalized = key(name)
        return normalized == key(player.displayName) ||
            (player.previousDisplayName.isNotBlank() && normalized == key(player.previousDisplayName))
    }

    private fun key(name: String): String = name.replace('_', ' ').replace('\u00a0', ' ').trim().lowercase(Locale.ROOT)
}

class StaffSocialVisibilityScript @Inject constructor(private val visibility: StaffSocialVisibility) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<StaffVisibilityChanged> { visibility.refresh(player) }
        onEvent<SessionStateEvent.Delete> { visibility.clear(player) }
    }
}
