package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public data class PlayerActionCompleteEvent(
    public val player: Player,
    public val action: PlayerAction,
    public val target: String,
    public val coords: CoordGrid = player.coords,
    public val count: Int = 1,
) : UnboundEvent

public enum class PlayerAction {
    AgilityLap,
    BurnLog,
    CookFood,
    Pickpocket,
    LearnAge,
    EssenceMineTeleport,
    EnterUnderground,
    CraftRune,
    CraftItem,
    CrossShortcut,
    FairyRingTravel,
    CastTeleport,
    SlayerAssignment,
    CatchCreature,
    BuyDevice,
    CraftRuneBatch,
    CastUtility,
    EnchantItem,
    WakaTravel,
    HarvestCrop,
    LightSource,
    PraySmite,
    CollectTears,
}
