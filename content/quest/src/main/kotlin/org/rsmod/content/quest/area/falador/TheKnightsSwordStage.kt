package org.rsmod.content.quest.area.falador

internal object TheKnightsSwordStage {
    const val NotStarted: Int = 0

    const val Started: Int = 1

    const val ReldoAdvised: Int = 2

    const val Stage3PieEaten: Int = 3

    const val ThurgoWantsPicture: Int = 4

    const val Stage5PortraitNamed: Int = 5

    const val ThurgoWantsMaterials: Int = 6

    const val Complete: Int = 7
}

internal object TheKnightsSwordRefs {
    const val Squire: String = "npc.squire"

    const val Thurgo: String = "npc.thurgo"

    const val Reldo: String = "npc.reldo"

    const val Pie: String = "obj.redberry_pie"

    const val Portrait: String = "obj.knights_portrait"

    const val BluriteOre: String = "obj.blurite_ore"

    const val IronBar: String = "obj.iron_bar"

    const val FaladianSword: String = "obj.faladian_sword"

    const val CupboardShut: String = "loc.vyvincupboardshut"

    const val CupboardOpen: String = "loc.vyvincupboardopen"

    const val IronBarsNeeded: Int = 2

    const val RecommendedCombatLevel: Int = 20

    const val RequiredMining: Int = 10

    const val SmithingXpReward: Double = 12_725.0

    const val CompletionJingle: Int = 153
}
