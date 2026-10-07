package org.rsmod.content.minigames.fishingtrawler

object TrawlerConstants {

    const val INSTANCE_KEY = "fishing_trawler"

    const val MURPHY_LOBBY = "npc.murphy_lobby"

    const val MAIN_SHIP_REGION = 7499

    const val SHIP_MX = 29
    const val SHIP_MZ = 75
    const val MURPHY_ON_SHIP_LX = 29
    const val MURPHY_ON_SHIP_LZ = 25
    const val TENTACLE_PORT_LX = 27
    const val TENTACLE_PORT_LZ = 30
    const val TENTACLE_STARBOARD_LX = 27
    const val TENTACLE_STARBOARD_LZ = 18

    const val LOBBY_X = 2671
    const val LOBBY_Z = 3172

    const val SINK_EXIT_X = 2670
    const val SINK_EXIT_Z = 3242

    const val LOBBY_WAIT_TICKS = 100

    const val SAIL_TICKS = 500

    const val DOCK_TICKS = 100

    const val CUTSCENE_TICKS = 25

    // Hull leaks use varbit transforms, not loc replacement.
    const val BASE_HULL_LOC = "loc.trawler_hull_00"
    const val HULL_SLOT_COUNT = 16
    const val HULL_LEAK_TRANSFORM_LOC = "loc.trawler_hull_leak"

    fun hullVarbit(slot: Int): String = "varbit.trawler_hull_%02d".format(slot)

    // Transform indices: 0/3 intact, 1 patched, 2 leaking.
    const val HULL_STATE_INTACT = 0
    const val HULL_STATE_PATCHED = 1
    const val HULL_STATE_LEAKING = 2

    // TODO: Add multiplayer leak scaling; the solo reopen delay is unverified.
    const val LEAK_REOPEN_DELAY_TICKS = 5

    // Water rise timing is approximate.
    const val WATER_RISE_PER_TICK = 1
    const val WATER_RISE_INTERVAL_TICKS = 4
    const val WATER_SINK_THRESHOLD = 100

    const val SWAMP_PASTE_ITEM = "obj.swamppaste"

    // Rails use shared loc replacement, unlike varbit-driven hull leaks.
    const val RAIL_BROKEN_OP = "loc.trawler_shiprail_broken_op"
    const val RAIL_FIXED = "loc.trawler_shiprail"

    const val NET_LEFT = "loc.trawlernet_l"
    const val NET_RIGHT = "loc.trawlernet_r"
    const val NET_REWARD = "loc.trawlernetland"
    const val ROPE_ITEM = "obj.rope"

    // Spawn trawler_tentacle_op; its port/starboard multinpc parents render nothing.
    const val TENTACLE_NPC = "npc.trawler_tentacle_op"

    const val TENTACLE_ATTACK_COUNT_SOLO = 2
    const val TENTACLE_ATTACK_INTERVAL_TICKS = 450
    const val TENTACLE_FIRST_ATTACK_DELAY_TICKS = 450
    const val TENTACLE_DESPAWN_AFTER_TICKS = 100

    const val BUCKET_EMPTY = "obj.bucket_bailing"
    const val BUCKET_FULL = "obj.bucket_bailingfull"

    const val POINTS_BAIL = 2
    const val POINTS_LEAK_FILL = 5
    const val POINTS_NET_REPAIR = 7
    const val POINTS_TENTACLE_CHOP = 10
    const val POINTS_RAIL_FIX = 10

    const val MIN_CONTRIBUTION_TO_CLAIM = 50
    const val MAX_CONTRIBUTION = 255

    const val CONSTRUCTION_XP_LEAK_FILL = 5.0
    const val CONSTRUCTION_XP_RAIL_FIX = 5.0
    const val CRAFTING_XP_NET_REPAIR = 5.0
    const val WOODCUTTING_XP_TENTACLE_CHOP = 5.0

    const val MIN_FISHING_LEVEL_FOR_REWARD = 15

    const val ANGLER_PIECE_CHANCE = 12

    const val HERON_CHANCE_MIN_CONTRIB = 5000
    const val HERON_CHANCE_FULL_METER = 2500

    // Approximate catch formula; the live formula is unknown.
    const val CATCH_PER_CONTRIBUTION_ACTION = 0.35
    const val CATCH_BASE = 8
}
