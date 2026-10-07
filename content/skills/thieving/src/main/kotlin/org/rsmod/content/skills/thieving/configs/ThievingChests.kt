package org.rsmod.content.skills.thieving.configs

// The native thieving_chest table covers Varlamore pirate chests, not these classic chests.
data class ThievingChest(
    val loc: String,
    val openLoc: String,
    val levelReq: Int,
    val xp: Double,
    val respawnTicks: Int,
    val trapped: Boolean,
    val requiresLockpick: Boolean,
    val trapDamage: TrapDamage,
)

// Only nature-rune and Chaos Druid Tower damage formulas are documented; other formulas are approximations.
enum class TrapDamage {
    None,

    Standard,

    Severe;

    fun calculate(currentHitpoints: Int): Int =
        when (this) {
            None -> 0
            Standard -> (currentHitpoints * 12) / 100 + 3
            Severe -> (9 * currentHitpoints + 225) / 50
        }
}

object ThievingChests {
    const val OPEN_ANIM: String = "seq.human_openchest"
    const val PICKLOCK_ANIM: String = "seq.human_picklock_chest"
    const val OPEN_SOUND: String = "synth.CHEST_OPEN"
    const val PICKLOCK_SOUND: String = "synth.PICK_LOCK"
    const val LOCKPICK: String = "obj.lockpick"

    const val OPEN_QUEUE: String = "queue.thieving_chest_open"

    val all: List<ThievingChest> =
        listOf(
            ThievingChest(
                loc = "loc.wilderness_rogue_chest",
                openLoc = "loc.wilderness_rogue_chest_open",
                levelReq = 84,
                xp = 701.7,
                respawnTicks = 34,
                trapped = true,
                requiresLockpick = false,
                trapDamage = TrapDamage.Standard,
            ),
            ThievingChest(
                loc = "loc.trapchest1",
                openLoc = "loc.emptytrapchest",
                levelReq = 13,
                xp = 7.8,
                respawnTicks = 12,
                trapped = true,
                requiresLockpick = false,
                trapDamage = TrapDamage.Standard,
            ),
            ThievingChest(
                loc = "loc.trapchest2",
                openLoc = "loc.emptytrapchest",
                levelReq = 28,
                xp = 25.0,
                respawnTicks = 25,
                trapped = true,
                requiresLockpick = false,
                trapDamage = TrapDamage.Standard,
            ),
            ThievingChest(
                loc = "loc.trapchest3",
                openLoc = "loc.emptytrapchest",
                levelReq = 43,
                xp = 125.0,
                respawnTicks = 84,
                trapped = true,
                requiresLockpick = false,
                trapDamage = TrapDamage.Standard,
            ),
            ThievingChest(
                loc = "loc.pickchest3",
                openLoc = "loc.emptypickchest",
                levelReq = 47,
                xp = 150.0,
                respawnTicks = 150,
                trapped = false,
                requiresLockpick = true,
                trapDamage = TrapDamage.None,
            ),
            ThievingChest(
                loc = "loc.trapchest4",
                openLoc = "loc.emptytrapchest",
                levelReq = 59,
                xp = 250.0,
                respawnTicks = 225,
                trapped = true,
                requiresLockpick = false,
                trapDamage = TrapDamage.Severe,
            ),
            // TODO: Verify Ardougne Castle trap damage; it currently borrows the Chaos Druid Tower formula.
            ThievingChest(
                loc = "loc.trapchest5",
                openLoc = "loc.emptytrapchest",
                levelReq = 72,
                xp = 500.0,
                respawnTicks = 834,
                trapped = true,
                requiresLockpick = false,
                trapDamage = TrapDamage.Severe,
            ),
        )

    private val byLoc: Map<String, ThievingChest> = all.associateBy(ThievingChest::loc)

    fun forLoc(loc: String): ThievingChest? = byLoc[loc]
}
