package org.rsmod.api.net.central

import dev.openrune.ServerCacheManager
import net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettingsPrivateChat
import net.rsprot.protocol.game.outgoing.social.FriendListLoaded
import net.rsprot.protocol.game.outgoing.social.UpdateFriendList
import net.rsprot.protocol.game.outgoing.social.UpdateIgnoreList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.player.StaffVisibilityChanged
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class StaffSocialVisibilityTest {
    @Test
    fun `login snapshot hides local staff presence and preserves ordinary friends and packet order`() {
        val f = Fixture()
        f.staff.staffInvisible = true
        f.snapshot(listOf(friend("Staff Member", 301), friend("Visible", 302)))
        assertEquals(listOf(FriendListLoaded::class.java, ChatFilterSettingsPrivateChat::class.java,
            UpdateFriendList::class.java, UpdateIgnoreList::class.java), f.client.messages.map { it.javaClass })
        assertEquals(listOf(0, 302), f.friends().map { it.worldId })
        assertEquals(listOf("Staff Member", "Visible"), f.friends().map { it.name })
    }

    @Test
    fun `toggle event sends offline and restores original world only to known friends`() {
        val f = Fixture()
        f.snapshot(listOf(friend("Staff Member", 301)))
        f.client.messages.clear()
        f.toggle(true)
        assertEquals(0, f.friends().single().worldId)
        assertTrue(f.strangerClient.messages.isEmpty())
        f.toggle(false)
        assertEquals(301, f.friends().single().worldId)
        assertTrue(f.strangerClient.messages.isEmpty())
    }

    @Test
    fun `presence pushes remain hidden and toggle restores latest central offline decision`() {
        val f = Fixture()
        f.staff.staffInvisible = true
        f.push(301)
        assertEquals(0, f.friends().single().worldId)
        f.push(0)
        f.toggle(false)
        assertEquals(0, f.friends().single().worldId, "Do not undo Central private-chat offline status")
    }

    @Test
    fun `new observers and changed world presence are masked while staff is hidden`() {
        val f = Fixture()
        f.staff.staffInvisible = true
        f.snapshot(listOf(friend("Staff Member", 301)))
        f.push(302)
        assertEquals(0, f.friends().single().worldId)
        f.toggle(false)
        val restored = f.friends().single() as UpdateFriendList.OnlineFriend
        assertEquals(302, restored.worldId)
        assertEquals("Test 2", restored.worldName)
    }

    @Test
    fun `deleted friends empty snapshots and disconnected recipients cannot be reintroduced by toggle`() {
        val f = Fixture()
        f.snapshot(listOf(friend("Staff Member", 301)))
        f.visibility.forget(f.owner, "staff_member")
        f.client.messages.clear()
        f.toggle(true)
        assertTrue(f.client.messages.isEmpty())
        f.snapshot(listOf(friend("Staff Member", 301)))
        f.owner.writeCentralSocialSnapshotEmpty(f.visibility)
        f.client.messages.clear()
        f.toggle(false)
        assertTrue(f.client.messages.isEmpty())
        f.snapshot(listOf(friend("Staff Member", 301)))
        f.events.publish(SessionStateEvent.Delete(f.owner))
        f.client.messages.clear()
        f.toggle(true)
        assertTrue(f.client.messages.isEmpty())
    }

    @Test
    fun `current and previous normalized names are masked and replacement snapshots forget removed names`() {
        val f = Fixture()
        f.staff.previousDisplayName = "Old Staff"
        f.staff.staffInvisible = true
        f.snapshot(listOf(friend("STAFF_MEMBER", 301), friend("old_staff", 301)))
        assertTrue(f.friends().all { it.worldId == 0 })
        f.snapshot(listOf(friend("Visible", 302)))
        f.client.messages.clear()
        f.toggle(false)
        assertTrue(f.client.messages.isEmpty())
    }

    private class Fixture {
        val players = PlayerList()
        val visibility = StaffSocialVisibility(players)
        val events = EventBus()
        val client = RecordingClient()
        val strangerClient = RecordingClient()
        val owner = Player(client).apply { displayName = "Observer"; characterId = 1 }
        val staff = Player().apply { displayName = "Staff Member"; characterId = 2 }
        private val stranger = Player(strangerClient).apply { displayName = "Stranger"; characterId = 3 }
        private val config = ServerConfig("Test", 43594, 240, "test", 301)

        init {
            players[1] = owner; players[2] = staff; players[3] = stranger
            with(StaffSocialVisibilityScript(visibility)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun snapshot(friends: List<WorldLinkFrameSpecs.CentralSocialFriend>) {
            owner.writeCentralSocialSnapshot(WorldLinkFrameSpecs.CentralSocialSnapshot(0, 1, 0, friends, emptyList()), visibility)
        }

        fun push(world: Int) {
            owner.writeCentralFriendPresence(ServerFriendPresencePayload(1, 2, world, "Staff Member", null), config, visibility)
        }

        fun toggle(hidden: Boolean) {
            staff.staffInvisible = hidden
            events.publish(StaffVisibilityChanged(staff))
        }

        fun friends(): List<UpdateFriendList.Friend> = client.messages.filterIsInstance<UpdateFriendList>().last().friends
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private fun friend(name: String, world: Int) = WorldLinkFrameSpecs.CentralSocialFriend(name, null, world)

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
