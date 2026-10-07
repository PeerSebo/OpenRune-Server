package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.area.MapAreaDecoder
import dev.openrune.map.area.MapAreaDefinitions
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import it.unimi.dsi.fastutil.shorts.ShortArrayList
import java.nio.file.Path
import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.MusicModernRow
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsWorkshop
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FoundryMusicTest {
    @Test
    fun `native music is routed outside inside and in the private quest workshop`() {
        val events = EventBus()
        val world = FoundryQuestTestWorld(
            FoundryGame(FoundryInterfaces(events), events, XpModifiers(emptySet())), events)
        world.loadMap()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = client
            coords = SleepingGiantsWorkshop.OUTSIDE
        }
        val random = DefaultGameRandom(1)
        val repository = MusicRepository(random).apply { load() }
        val music = MusicPlayer::class.java
            .getDeclaredConstructor(GameRandom::class.java, MusicRepository::class.java)
            .apply { isAccessible = true }.newInstance(random, repository)
        VarPlayerIntMapSetter.set(player, "varbit.option_master_volume_desktop", 62)
        VarPlayerIntMapSetter.set(player, "varbit.option_music_desktop", 5)

        val outside = areaAt(player.coords)
        assertEquals("area.al_kharid", outside)
        music.enterArea(player, outside)
        val outsideTrack = client.messages.filterIsInstance<MidiSongV2>().last().id
        assertTrue(checkNotNull(repository.getModernArea(outside)).any { it.midi.id == outsideTrack })
        player.coords = SleepingGiantsWorkshop.ENTRANCE
        val inside = areaAt(player.coords)
        assertEquals("area.the_foundry", inside)
        music.exitArea(player, outside)
        music.enterArea(player, inside)
        assertEquals(406, client.messages.filterIsInstance<MidiSongV2>().last().id)

        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty().copy(
            getCollision = { world.collision },
            getEventBus = { events },
            getAreaChecker = { AreaChecker(world.regions, areaIndex) },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
        ))
        try {
            assertNotNull(world.workshop.enter(access))
            assertNotEquals(SleepingGiantsWorkshop.ENTRANCE, player.coords)
            val normalized = world.regions.normalizeCoords(player.coords)
            assertEquals(SleepingGiantsWorkshop.ENTRANCE, normalized)
            assertEquals(inside, areaAt(normalized))
            val songs = client.messages.filterIsInstance<MidiSongV2>().size
            music.enterArea(player, areaAt(normalized))
            assertEquals(songs, client.messages.filterIsInstance<MidiSongV2>().size)
        } finally {
            world.workshop.leave(player)
        }
    }

    @Test
    fun `served cache contains the foundry outside tracks and quest completion jingle`() {
        val rows = MusicModernRow.all().filter { it.area in setOf("the_foundry", "al_kharid") }
        assertEquals(2, rows.size)
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            for (row in rows) for (track in row.tracks) {
                assertTrue(checkNotNull(cache.data(6, track.midi.id, 0)).isNotEmpty(), track.displayname)
            }
            assertTrue(checkNotNull(cache.data(11, 154, 0)).isNotEmpty())
        } finally {
            cache.close()
        }
    }

    private fun areaAt(coords: CoordGrid): String {
        val areas = ShortArrayList()
        areaIndex.putAreasIncludingParents(coords, areas)
        val music = MusicModernRow.all().associateBy { "area.${it.area}".asRSCM() }
        return "area." + areas.toShortArray().map { music[it.toInt()] }.filterNotNull().single().area
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
    }

    companion object {
        private val areaIndex = AreaIndex()

        @JvmStatic @BeforeAll
        fun cache() {
            val cache = ServerCacheManager.init(241)
            try {
                for (coords in listOf(SleepingGiantsWorkshop.OUTSIDE, SleepingGiantsWorkshop.ENTRANCE)) {
                    val square = MapSquareKey.from(coords)
                    val data = checkNotNull(cache.data(MAPS, square.id, 7))
                    MapAreaDefinitions.putAreas(areaIndex, square, MapAreaDecoder.decode(InlineByteBuf(data)))
                }
            } finally {
                cache.close()
            }
        }
    }
}
