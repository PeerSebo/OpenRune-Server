package org.rsmod.api.player.protect

import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.api.player.interact.WornInteractions
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PlayerList
import org.rsmod.routefinder.collision.CollisionFlagMap

public data class ProtectedAccessContext(
    private val getRandom: () -> GameRandom,
    private val getEventBus: () -> EventBus,
    private val getNpcList: () -> NpcList,
    private val getPlayerList: () -> PlayerList,
    private val getCollision: () -> CollisionFlagMap,
    private val getAreaChecker: () -> AreaChecker,
    private val getAlignment: () -> TextAlignment,
    private val getLocInteractions: () -> LocInteractions,
    private val getNpcInteractions: () -> NpcInteractions,
    private val getPlayerInteractions: () -> PlayerInteractions,
    private val getHeldInteractions: () -> HeldInteractions,
    private val getWornInteractions: () -> WornInteractions,
    private val getMusicPlayer: () -> MusicPlayer,
    private val getMarketPrices: () -> MarketPrices,
    private val getInstantHitProcessor: () -> InstantPlayerHitProcessor,
    private val getTeleportValidator: () -> PlayerTeleportValidator,
    private val getWorldRepo: () -> WorldRepository,
    private val getHitModifier: () -> PlayerHitModifier,
) {
    public val random: GameRandom by lazyLoad { getRandom() }
    public val eventBus: EventBus by lazyLoad { getEventBus() }
    public val hitModifier: PlayerHitModifier by lazyLoad { getHitModifier() }
    public val npcList: NpcList by lazyLoad { getNpcList() }
    public val playerList: PlayerList by lazyLoad { getPlayerList() }
    public val collision: CollisionFlagMap by lazyLoad { getCollision() }
    public val areaChecker: AreaChecker by lazyLoad { getAreaChecker() }
    public val alignment: TextAlignment by lazyLoad { getAlignment() }
    public val locInteractions: LocInteractions by lazyLoad { getLocInteractions() }
    public val npcInteractions: NpcInteractions by lazyLoad { getNpcInteractions() }
    public val playerInteractions: PlayerInteractions by lazyLoad { getPlayerInteractions() }
    public val heldInteractions: HeldInteractions by lazyLoad { getHeldInteractions() }
    public val wornInteractions: WornInteractions by lazyLoad { getWornInteractions() }
    public val musicPlayer: MusicPlayer by lazyLoad { getMusicPlayer() }
    public val marketPrices: MarketPrices by lazyLoad { getMarketPrices() }
    public val instantHitProcessor: InstantPlayerHitProcessor by lazyLoad {
        getInstantHitProcessor()
    }
    public val teleportValidator: PlayerTeleportValidator by lazyLoad { getTeleportValidator() }

    public val worldRepo: WorldRepository by lazyLoad { getWorldRepo() }
}

private fun <T> lazyLoad(init: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, init)
