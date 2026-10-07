package org.rsmod.content.skills.thieving.configs

data class PickLockDoor(
    val closedLoc: String,
    val openLoc: String,
    val levelReq: Int,
    val xp: Double,
    val requiresLockpick: Boolean,
    val trap: DoorTrap,
    val gatePanel: GatePanel = GatePanel.None,
    val gatePartnerLoc: String? = null,
    val gatePartnerOpenLoc: String? = null,
) {
    val successRates: Pair<Int, Int> = ThievingRates.fittedDoorRates(levelReq)
}

enum class GatePanel {
    None,
    Left,
    Right,
}

enum class DoorTrap {
    None,

    SixPercentOfMax,

    TenPercentOfMax,

    TenPercentOfCurrent;

    fun calculate(currentHitpoints: Int, maxHitpoints: Int): Int =
        when (this) {
            None -> 0
            SixPercentOfMax -> (maxHitpoints * 6) / 100
            TenPercentOfMax -> (maxHitpoints * 10) / 100
            TenPercentOfCurrent -> if (currentHitpoints <= 10) 0 else (currentHitpoints * 10) / 100
        }
}

object ThievingDoors {
    const val PICKLOCK_ANIM: String = "seq.human_picklock_cagedoor"
    const val PICKLOCK_SOUND: String = "synth.PICK_LOCK"
    const val LOCKPICK: String = "obj.lockpick"

    const val OPEN_DURATION: Int = 500

    const val PICK_QUEUE: String = "queue.thieving_door_pick"

    val all: List<PickLockDoor> =
        listOf(
            PickLockDoor(
                closedLoc = "loc.con_contract_ardy_west_door_locked",
                openLoc = "loc.poordooropen",
                levelReq = 13,
                xp = 15.0,
                requiresLockpick = false,
                trap = DoorTrap.TenPercentOfCurrent,
            ),
            PickLockDoor(
                closedLoc = "loc.picklock1",
                openLoc = "loc.poordooropen",
                levelReq = 1,
                xp = 3.2,
                requiresLockpick = false,
                trap = DoorTrap.None,
            ),
            PickLockDoor(
                closedLoc = "loc.picklock2",
                openLoc = "loc.poordooropen",
                levelReq = 16,
                xp = 15.0,
                requiresLockpick = false,
                trap = DoorTrap.None,
            ),
            PickLockDoor(
                closedLoc = "loc.picklock3_l",
                openLoc = "loc.metalgateopenl",
                levelReq = 31,
                xp = 25.0,
                requiresLockpick = false,
                trap = DoorTrap.None,
                gatePanel = GatePanel.Left,
                gatePartnerLoc = "loc.picklock3_r",
                gatePartnerOpenLoc = "loc.metalgateopenr",
            ),
            PickLockDoor(
                closedLoc = "loc.picklock3_r",
                openLoc = "loc.metalgateopenr",
                levelReq = 31,
                xp = 25.0,
                requiresLockpick = false,
                trap = DoorTrap.None,
                gatePanel = GatePanel.Right,
                gatePartnerLoc = "loc.picklock3_l",
                gatePartnerOpenLoc = "loc.metalgateopenl",
            ),
            PickLockDoor(
                closedLoc = "loc.toollock2",
                openLoc = "loc.prisondooropen",
                levelReq = 23,
                xp = 22.5,
                requiresLockpick = true,
                trap = DoorTrap.None,
            ),
            PickLockDoor(
                closedLoc = "loc.toollock3",
                openLoc = "loc.poordooropen",
                levelReq = 39,
                xp = 35.0,
                requiresLockpick = true,
                trap = DoorTrap.None,
            ),
            PickLockDoor(
                closedLoc = "loc.picklock4",
                openLoc = "loc.poordooropen",
                levelReq = 46,
                xp = 37.5,
                requiresLockpick = false,
                trap = DoorTrap.SixPercentOfMax,
            ),
            PickLockDoor(
                closedLoc = "loc.picklock5",
                openLoc = "loc.elfdooropen",
                levelReq = 61,
                xp = 50.0,
                requiresLockpick = false,
                trap = DoorTrap.TenPercentOfMax,
            ),
            PickLockDoor(
                closedLoc = "loc.toollock5",
                openLoc = "loc.poordooropen",
                levelReq = 82,
                xp = 50.0,
                requiresLockpick = true,
                trap = DoorTrap.None,
            ),
        )

    private val byLoc: Map<String, PickLockDoor> = all.associateBy(PickLockDoor::closedLoc)

    fun forLoc(loc: String): PickLockDoor? = byLoc[loc]
}
