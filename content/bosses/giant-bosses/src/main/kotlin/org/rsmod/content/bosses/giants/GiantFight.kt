package org.rsmod.content.bosses.giants

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal enum class GiantBoss(
    val label: String,
    val npc: String,
    val key: String,
    val chest: String,
    val openChest: String,
    val kc: String,
    val entry: CoordGrid,
    val exit: CoordGrid,
    val spawn: CoordGrid,
) {
    OBOR("Obor", "npc.hillgiant_boss", "obj.hillgiant_boss_key",
        "loc.giantkey_chest_closed", "loc.giantkey_chest_open", "varp.total_hillgiant_boss_kills",
        CoordGrid(3092, 9814), CoordGrid(3096, 9833), CoordGrid(3091, 9797)),
    BRYOPHYTA("Bryophyta", "npc.gb_mossgiant", "obj.mossy_key",
        "loc.mossykey_chest_closed", "loc.mossykey_chest_open", "varp.total_bryophyta_kills",
        CoordGrid(3214, 9937), CoordGrid(3174, 9900), CoordGrid(3220, 9933));

    val instanceKey: String get() = "giant_boss_${name.lowercase()}"
    internal val unlockVar = "varbit.giant_boss_${name.lowercase()}_unlocked"
    internal val legacyUnlock = AttributeKey<Boolean>(persistenceKey = "giant_boss_${name.lowercase()}_unlocked")
    fun unlocked(player: Player): Boolean {
        player.migrateGiantUnlocks()
        return player.vars[unlockVar] != 0
    }
    fun unlock(player: Player) {
        player.migrateGiantUnlocks()
        VarPlayerIntMapSetter.set(player, unlockVar, 1)
    }
}

internal fun Player.migrateGiantUnlocks() {
    val imported = vars["varbit.giant_boss_unlocks_migrated"] != 0
    for (boss in GiantBoss.entries) {
        if (!imported && attr[boss.legacyUnlock] == true) {
            VarPlayerIntMapSetter.set(this, boss.unlockVar, 1)
        }
        if (attr.has(boss.legacyUnlock)) attr.remove(boss.legacyUnlock)
    }
    if (!imported) VarPlayerIntMapSetter.set(this, "varbit.giant_boss_unlocks_migrated", 1)
}

internal class GiantLife {
    var dead: Boolean = false
        private set
    var chestAttempted: Boolean = false
        private set
    var restartAt: Int? = null
        private set

    fun died(): Boolean {
        if (dead) return false
        dead = true
        return true
    }

    fun attemptChest(tick: Int): Boolean {
        if (!dead || chestAttempted) return false
        chestAttempted = true
        restartAt = tick + 15
        return true
    }

    fun ready(tick: Int): Boolean = restartAt?.let { tick >= it } == true
}

internal class GiantRoom(val id: InstanceId, val owner: Player, val kind: GiantBoss, val arrival: CoordGrid) {
    var life = GiantLife()
    var boss: Npc? = null
    var enteredPit = kind == GiantBoss.BRYOPHYTA
    val growthlings = mutableSetOf<Npc>()
    val finishing = mutableSetOf<Npc>()
    var nextGrowthlings = 0
    fun tile(source: CoordGrid): CoordGrid = CoordGrid(
        arrival.x + source.x - kind.entry.x, arrival.z + source.z - kind.entry.z, arrival.level)
    fun source(tile: CoordGrid): CoordGrid = CoordGrid(
        kind.entry.x + tile.x - arrival.x, kind.entry.z + tile.z - arrival.z, kind.entry.level)
}

internal object GiantRules {
    fun mayEnter(unlocked: Boolean, keys: Int): Boolean = unlocked || keys > 0
    fun growthlingDamage(hitpoints: Int, damage: Int, cutting: Boolean): Int =
        if (cutting) hitpoints else damage.coerceAtMost((hitpoints - 1).coerceAtLeast(0))
    fun distance(origin: CoordGrid, size: Int, target: CoordGrid): Int =
        if (origin.level != target.level) Int.MAX_VALUE else maxOf(
            (origin.x - target.x).coerceAtLeast(0), (target.x - origin.x - size + 1).coerceAtLeast(0),
            (origin.z - target.z).coerceAtLeast(0), (target.z - origin.z - size + 1).coerceAtLeast(0))
    fun oborRangedDamage(damage: Int, protecting: Boolean): Int = if (protecting) damage / 2 else damage
}
