package org.rsmod.api.combat.manager

import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.ranged.RangedAmmunition
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

public class RangedAmmoManager
@Inject
constructor(
    private val random: GameRandom,
    private val eventBus: EventBus,
    private val collision: CollisionFlagMap,
    private val worldQueues: WorldQueueList,
    private val objRepo: ObjRepository,
) {
    public fun attemptAmmoUsage(
        player: Player,
        weapon: ItemServerType,
        ammo: ItemServerType?,
    ): Boolean = RangedAmmunition.attemptAmmoUsage(player, weapon, ammo)

    /** dropChance is a denominator: ammunition drops with probability 1 / dropChance. */
    public fun useQuiverAmmo(
        player: Player,
        quiverType: ItemServerType,
        dropCoord: CoordGrid,
        dropDelay: Int,
        dropChance: Int = RangedAmmunition.DEFAULT_AMMO_DROP_RATE,
        dropDuration: Int = RangedAmmunition.DEFAULT_AMMO_DROP_DURATION,
        useCount: Int = 1,
    ): Unit =
        useAmmo(
            player = player,
            ammoWearpos = Wearpos.Quiver,
            ammoType = quiverType,
            ammoCount = useCount,
            dropCoord = dropCoord,
            dropDelay = dropDelay,
            dropChance = dropChance,
            dropDuration = dropDuration,
        )

    /** dropChance is a denominator: ammunition drops with probability 1 / dropChance. */
    public fun useThrownWeapon(
        player: Player,
        weaponType: ItemServerType,
        dropCoord: CoordGrid,
        dropDelay: Int,
        dropChance: Int = RangedAmmunition.DEFAULT_AMMO_DROP_RATE,
        dropDuration: Int = RangedAmmunition.DEFAULT_AMMO_DROP_DURATION,
        useCount: Int = 1,
    ): Unit =
        useAmmo(
            player = player,
            ammoWearpos = Wearpos.RightHand,
            ammoType = weaponType,
            ammoCount = useCount,
            dropCoord = dropCoord,
            dropDelay = dropDelay,
            dropChance = dropChance,
            dropDuration = dropDuration,
        )

    private fun useAmmo(
        player: Player,
        ammoWearpos: Wearpos,
        ammoType: ItemServerType,
        ammoCount: Int,
        dropCoord: CoordGrid,
        dropDelay: Int,
        dropChance: Int,
        dropDuration: Int,
    ) {
        val conserve = RangedAmmunition.conserveAmmo(player, random)
        if (!conserve) {
            RangedAmmunition.detractAmmo(player, ammoWearpos, ammoType, ammoCount, eventBus)
        }

        if (!conserve && random.randomBoolean(dropChance)) {
            RangedAmmunition.attemptAmmoDrop(
                player = player,
                delay = dropDelay,
                ammoType = ammoType,
                ammoCount = ammoCount,
                dropCoord = dropCoord,
                dropDuration = dropDuration,
                collision = collision,
                worldQueues = worldQueues,
                objRepo = objRepo,
            )
        }
    }
}
