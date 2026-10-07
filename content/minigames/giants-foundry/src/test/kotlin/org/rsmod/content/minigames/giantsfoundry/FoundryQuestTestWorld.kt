package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsCutscene
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsService
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsWorkshop
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class FoundryQuestTestWorld(game: FoundryGame, events: EventBus) {
    val collision = CollisionFlagMap()
    private val clock = MapClock(100)
    private val storage = LocZoneStorage()
    private val updates = ZoneUpdateMap()
    private val normal = LocRegistryNormal(updates, collision, storage)
    val npcs = NpcList()
    private val npcRegistry = NpcRegistry(npcs, collision, events)
    val regions = RegionRegistry(
        RegionListSmall(), RegionListLarge(), normal, collision, storage, npcRegistry,
        ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet(),
    )
    val locs = LocRepository(clock, LocRegistry(storage, normal,
        LocRegistryRegion(updates, collision, storage, regions)), regions)
    val workshop = SleepingGiantsWorkshop(RegionRepository(regions), locs,
        NpcRepository(clock, npcRegistry, npcs), collision)
    private val random = DefaultGameRandom(1)
    private val musicRepository = MusicRepository(random).apply { load() }
    val music = MusicPlayer::class.java
        .getDeclaredConstructor(GameRandom::class.java, MusicRepository::class.java)
        .apply { isAccessible = true }.newInstance(random, musicRepository)
    val service = SleepingGiantsService(game, workshop, SleepingGiantsCutscene(workshop), music)

    fun loadMap() {
        val cache = ServerCacheManager.init(241)
        try {
            val group = (52 shl 8) or 179
            val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
            val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
            for (level in 0..3) for (x in 3328..3391 step 8) for (z in 11456..11519 step 8) {
                collision.allocateIfAbsent(x, z, level)
            }
            collision.allocateIfAbsent(3360, 3149, 0)
            val builder = GameMapBuilder()
            val square = MapSquareKey(52, 179)
            GameMapDecoder.putMaps(collision, square, tiles)
            GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
            for ((packed, zone) in builder.zoneBuilders) storage.mapLocs[packed] = zone.build()
        } finally {
            cache.close()
        }
    }
}
