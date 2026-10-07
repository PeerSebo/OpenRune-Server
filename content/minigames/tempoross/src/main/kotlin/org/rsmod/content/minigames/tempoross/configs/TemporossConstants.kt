package org.rsmod.content.minigames.tempoross.configs

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

object TemporossConstants {
    const val INSTANCE_KEY: String = "tempoross"

    const val LOBBY_DURATION_TICKS: Int = 50

    const val MAX_PLAYERS: Int = 40

    // Pier approach spans the blocked x=3136 gap; ordinary adjacency cannot reach the ladder.
    val PIER_COORD: CoordGrid = CoordGrid(3137, 2840, 0)

    val DECK_COORD: CoordGrid = CoordGrid(3134, 2840, 0)

    val RETURN_COORD: CoordGrid = CoordGrid(3153, 2839, 0)

    fun isAtBoarding(coords: CoordGrid): Boolean =
        coords.level == 0 &&
            kotlin.math.abs(coords.x - DECK_COORD.x) <= BOARDING_RADIUS_X &&
            kotlin.math.abs(coords.z - DECK_COORD.z) <= BOARDING_RADIUS_Z

    private const val BOARDING_RADIUS_X: Int = 6
    private const val BOARDING_RADIUS_Z: Int = 5

    const val LADDER: String = "loc.tempoross_lobby_ladder"
    const val LADDER_AP_RANGE: Int = 2

    const val FERRY_BOAT: String = "loc.tempoross_lobby_boat"
    const val FERRY_TO_LOBBY: String = "npc.tempoross_ferry_to_lobby_npc"
    const val FERRY_FROM_LOBBY: String = "npc.tempoross_ferry_from_lobby_npc"
    const val LOBBY_HOST: String = "npc.tempoross_lobby_host"
    const val LOBBY_ANNOUNCER: String = "npc.tempoross_lobby_announcer"

    val MAINLAND_FERRY_COORD: CoordGrid = CoordGrid(0, 51, 49, 7, 7)

    const val MAINLAND_BOAT_MIN_X: Int = 3200

    const val ARENA_REGION_ID: Int = (47 shl 8) or 44

    private const val ARENA_MX = 47
    private const val ARENA_MZ = 44

    private fun arena(x: Int, z: Int): CoordGrid = CoordGrid(x, z, 0)

    val ARENA_ENTER: CoordGrid = arena(3036, 2850)

    val ARENA_ENTER_LOCAL: RegionLocal =
        RegionLocal(0, ARENA_MX, ARENA_MZ, ARENA_ENTER.x - (ARENA_MX shl 6), ARENA_ENTER.z - (ARENA_MZ shl 6))

    // Copy all planes; mast and rigging geometry occupies upper archive levels.
    fun arenaArea(): InstanceArea {
        val template =
            RegionTemplate.create {
                copyAllLevels(ARENA_MX shl 3, ARENA_MZ shl 3) {
                    zoneWidth = 8
                    zoneLength = 8
                }
            }
        return InstanceArea.template(
            template = template,
            enterCoord = ARENA_ENTER_LOCAL,
            exitCoord = RETURN_COORD,
        )
    }

    data class ArenaSpawn(val npc: String, val source: CoordGrid, val facing: Direction = Direction.South)

    val AMMO_CRATES: List<ArenaSpawn> =
        listOf(
            ArenaSpawn("npc.tempoross_npc_crate_ammunition_1", arena(3038, 2850)),
            ArenaSpawn("npc.tempoross_npc_crate_ammunition_2", arena(3038, 2848)),
            ArenaSpawn("npc.tempoross_npc_crate_ammunition_3", arena(3056, 2850)),
            ArenaSpawn("npc.tempoross_npc_crate_ammunition_4", arena(3056, 2848)),
        )

    val CANNONEERS: List<ArenaSpawn> =
        listOf(
            ArenaSpawn("npc.tempoross_instance_cannoner_1", arena(3039, 2850), Direction.West),
            ArenaSpawn("npc.tempoross_instance_cannoner_2", arena(3039, 2848), Direction.West),
            ArenaSpawn("npc.tempoross_instance_cannoner_3", arena(3055, 2850), Direction.East),
            ArenaSpawn("npc.tempoross_instance_cannoner_4", arena(3055, 2848), Direction.East),
        )

    val HOSTS: List<ArenaSpawn> =
        listOf(
            ArenaSpawn("npc.tempoross_instance_host_w", arena(3036, 2852), Direction.East),
            ArenaSpawn("npc.tempoross_instance_host_n", arena(3043, 2869), Direction.South),
            ArenaSpawn("npc.tempoross_instance_host_e", arena(3058, 2846), Direction.West),
            ArenaSpawn("npc.tempoross_instance_host_s", arena(3041, 2834), Direction.North),
        )

    val FISHING_SPOTS: List<ArenaSpawn> =
        listOf(
            ArenaSpawn("npc.tempoross_harpoonfish_fishingspot_south", arena(3036, 2835)),
            ArenaSpawn("npc.tempoross_harpoonfish_fishingspot_north", arena(3037, 2868)),
        )

    const val BOSS_SURFACED: String = "npc.tempoross_boss_ready"
    const val BOSS_SUBMERGED: String = "npc.tempoross_boss_whirlpool"
    const val BOSS_ENRAGED: String = "npc.tempoross_boss_enraged"
    val BOSS_SOURCE: CoordGrid = arena(3043, 2845)

    const val SPIRIT_POOL_INACTIVE: String = "npc.tempoross_p2_fishingspot_inactive"
    const val SPIRIT_POOL_ACTIVE: String = "npc.tempoross_p2_fishingspot"
    val SPIRIT_POOL_SOURCES: List<CoordGrid> =
        listOf(
            arena(3046, 2853),
            arena(3046, 2843),
        )

    const val SHRINE: String = "loc.tempoross_shrine_fire"
    const val CRATE_HARPOON: String = "loc.tempoross_crate_harpoon"
    const val CRATE_BUCKET: String = "loc.tempoross_crate_bucket"
    const val CRATE_ROPE: String = "loc.tempoross_crate_rope"
    const val CRATE_HAMMER: String = "loc.tempoross_crate_hammer"

    const val HUD_INTERFACE: String = "interface.tempoross_hud"

    const val LOBBY_HUD_INTERFACE: String = "interface.tempoross_lobby_hud"
    const val LOBBY_HUD_PLAYERS: String = "component.tempoross_lobby_hud:players_waiting"
    const val LOBBY_HUD_NEXT: String = "component.tempoross_lobby_hud:next_encounter"

    // Fill the viewport at 164:1/161:1 so wave effects cross the scene.
    const val HUD_OVERLAY_TARGET: String = "component.toplevel_osrs_stretch:overlay_atmosphere"
    const val LOBBY_HUD_OVERLAY_TARGET: String = "component.toplevel_osrs_stretch:overlay_hud"

    val HUD_UPDATE_SCRIPT: Int get() = "clientscript.[clientscript,tempoross_hud_update]".asRSCM()

    val WAVE_ADVANCE_SCRIPT: Int get() = "clientscript.[clientscript,script4068]".asRSCM()
    val WAVE_RECEDE_SCRIPT: Int get() = "clientscript.[clientscript,script4069]".asRSCM()
    const val WAVE_ADVANCE_CLIENT_CYCLES = 300
    const val WAVE_RECEDE_CLIENT_CYCLES = 60
    const val WAVE_VISUAL_DELAY_TICKS = 2

    // Script 4074 divides storm intensity by this denominator.
    const val HUD_INTENSITY_MAX = 350

    const val HUD_CADENCE_TICKS: Int = 2

    const val VARBIT_POINTS: String = "varbit.tempoross_current_points"
    const val VARBIT_PERMITS: String = "varbit.tempoross_rewardpermits"

    const val RAW_HARPOONFISH: String = "obj.tempoross_raw_harpoonfish"
    const val CRYSTAL_HARPOONFISH = "obj.tempoross_crystallised_harpoonfish"
    const val WATER_BUCKET = "obj.bucket_water"
    const val SPECIAL_FISHING_SPOT = "npc.tempoross_harpoonfish_fishingspot_special"
    const val FIRE_NPC = "npc.tempoross_fire_hitbox"
    const val FIRE_LOC = "loc.tempoross_fire_visuals"
    const val TETHER_VARBIT = "varbit.tempoross_tethered"

    const val COOKED_HARPOONFISH: String = "obj.tempoross_harpoonfish"

    const val HARPOON: String = "obj.harpoon"
    const val BUCKET: String = "obj.bucket_empty"
    const val ROPE: String = "obj.rope"
    const val HAMMER: String = "obj.hammer"

    const val HARPOON_ANIM: String = "seq.human_harpoon"
    const val COOK_ANIM: String = "seq.human_cooking"

    const val REWARD_POOL: String = "loc.tempoross_rewardpool"
    const val SMALL_NET: String = "obj.net"
    const val BIG_NET: String = "obj.big_net"

    const val DEPART_FADE_TICKS: Int = 7

    const val WIN_RETURN_TICKS: Int = 37

    const val LOSS_RETURN_TICKS: Int = 29

    // Party-size scaling interpolates approximate energy/essence endpoints.
    const val ENERGY_PER_PLAYER: Int = 260
    const val ESSENCE_PER_PLAYER: Int = 250

    const val ENERGY_PER_RAW: Int = 10
    const val ENERGY_PER_COOKED: Int = 15

    const val POINTS_DEPOSIT_RAW: Int = 20
    const val POINTS_DEPOSIT_COOKED: Int = 65
    const val POINTS_CATCH: Int = 5
    const val POINTS_COOK: Int = 10
    const val POINTS_SCATTER: Int = 55

    // Storm-intensity cadence is provisional.
    const val INTENSITY_RAMP_TICKS: Int = 3
    const val INTENSITY_MAX: Int = 100

    const val ENERGY_REGEN_PER_TICK: Int = 2

    const val ESSENCE_DRAIN_MAX: Int = 12
    const val ESSENCE_DRAIN_MIN: Int = 4

    const val MAX_CYCLES: Int = 3

    // Fishing, cooking and bucket-fill cadences are provisional.
    const val ROLL_TICKS: Int = 5

    const val POOL_ROLL_TICKS: Int = 4

    const val COOK_INTERVAL_TICKS: Int = 3

    const val FILL_INTERVAL_TICKS: Int = 1

    const val PERMIT_BASE_POINTS: Int = 2000
    const val PERMIT_STEP_POINTS: Int = 700

    // Bridge archive plane 1 resolves to gameplay plane 0.
    data class Anchor(val source: CoordGrid, val normal: String, val broken: String, val tetherValue: Int, val mast: Boolean)
    val ANCHORS = listOf(
        Anchor(arena(3036, 2849), "loc.tempoross_mast_bottom_west", "loc.tempoross_mast_bottom_west_broken", 2, true),
        Anchor(arena(3058, 2849), "loc.tempoross_mast_bottom_east", "loc.tempoross_mast_bottom_east_broken", 3, true),
        Anchor(arena(3044, 2867), "loc.tempoross_totem_north", "loc.tempoross_totem_north_broken", 1, false),
        Anchor(arena(3043, 2833), "loc.tempoross_totem_south", "loc.tempoross_totem_south_broken", 4, false),
    )
    val CANNON_SOURCES = listOf(arena(3038, 2851), arena(3038, 2846), arena(3054, 2851), arena(3054, 2846))
    val PUMPS = listOf("loc.tempoross_water_pump", "loc.tempoross_water_pump_dock")
    val ANCHOR_OPS = ANCHORS.flatMap { listOf(it.normal, it.broken) } + listOf(
        "loc.tempoross_mast_bottom_tethered", "loc.tempoross_mast_bottom_untethered",
        "loc.tempoross_totem_tethered", "loc.tempoross_totem_untethered")
    val SPIRIT_ANGLER = listOf("obj.spirit_angler_hat", "obj.spirit_angler_top", "obj.spirit_angler_legs", "obj.spirit_angler_boots")

    // Exact normal-world hazard scheduling is approximate.
    const val CANNON_INTERVAL = 3
    const val PROJECTILE_TICKS = 3
    const val CANNON_DISABLE_TICKS = 50
    const val FIRST_LIGHTNING_TICKS = 30
    const val FIRST_WAVE_TICKS = 65
    const val FIRST_TORRENT_TICKS = 100
    const val SPECIAL_INTERVAL_TICKS = 100
    const val WAVE_WARNING_TICKS = 12
    const val LIGHTNING_WARNING_TICKS = 6
    const val FIRE_SPREAD_TICKS = 12
    const val MAX_FIRES = 80
    const val VULNERABLE_TICKS = 78
    const val ENRAGE_INTENSITY_PER_TICK = 3

    fun startingEnergy(players: Int): Int = scaledMeter(players, ENERGY_PER_PLAYER, 180)
    fun startingEssence(players: Int): Int = scaledMeter(players, ESSENCE_PER_PLAYER, 170)
    private fun scaledMeter(players: Int, solo: Int, mass: Int): Int {
        val count = players.coerceIn(1, MAX_PLAYERS)
        return count * (solo - (solo - mass) * (count - 1) / (MAX_PLAYERS - 1))
    }

    const val REQUIRED_FISHING_LEVEL: Int = 35
}
