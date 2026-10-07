package org.rsmod.content.skills.fletching.configs

object FletchingParams {

    const val KNIFE = "obj.knife"
    const val BOW_STRING = "obj.bow_string"

    const val FEATHER = "obj.feather"

    const val FEATHER_CONTENT = "content.fletching_feathers"

    const val HAMMER = "obj.hammer"

    const val CHISEL = "obj.chisel"

    const val XBOWS_CROSSBOW_STRING = "obj.xbows_crossbow_string"

    // Cadence is per recipe because a single prompt mixes shafts, bows, stocks and shields.

    const val ARROW_SHAFT_SUBSEQUENT_TICKS = 3

    const val STRING_TICKS = 2

    const val AMMO_SET_TICKS = 2

    const val JAVELIN_SET_TICKS = 1

    const val DART_BOLT_SET_TICKS = 2

    const val XBOW_ASSEMBLE_TICKS = 2

    const val XBOW_STRING_TICKS = 2

    const val BOLT_TIP_TICKS = 5

    const val ARROW_SET_SIZE = 15

    const val DART_BOLT_SET_SIZE = 10

    const val CARVE_ANIM = "seq.human_fletching"

    // Ammo assembly has no baked audio; carving/stringing sequences already contain their sounds.
    const val FLETCH_SOUND = "synth.FLETCH"

    const val FLETCH_CROSSBOW_SOUND = "synth.FLETCH_CROSSBOW"

    const val CHISEL_SOUND = "synth.CHISEL"

    const val CARVE_QUEUE = "queue.fletching_carve"
    const val STRING_QUEUE = "queue.fletching_string_bow"
    const val AMMO_QUEUE = "queue.fletching_ammo"
    const val JAVELIN_QUEUE = "queue.fletching_javelin"
    const val DART_BOLT_QUEUE = "queue.fletching_dart_bolt"
    const val XBOW_ASSEMBLE_QUEUE = "queue.fletching_xbow_assemble"
    const val XBOW_STRING_QUEUE = "queue.fletching_xbow_string"
    const val BOLT_TIP_QUEUE = "queue.fletching_bolt_tips"
    const val TIPPED_BOLT_QUEUE = "queue.fletching_tipped_bolts"
    const val BROAD_AMMO_QUEUE = "queue.fletching_broad_ammo"

    const val BROADER_FLETCHING_BIT = 7

    const val BROADER_FLETCHING_LOCKED_MESSAGE =
        "You need to have unlocked the ability to fletch broad ammunition from a Slayer master."
}
