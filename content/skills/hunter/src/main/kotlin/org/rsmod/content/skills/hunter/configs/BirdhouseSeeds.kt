package org.rsmod.content.skills.hunter.configs

object BirdhouseSeeds {

    private const val HOP_COUNT: Int = 10
    private const val WILDBLOOD_HOP_COUNT: Int = 5

    private const val ALLOTMENT_COUNT: Int = 10

    private const val FLOWER_COUNT: Int = 10

    private const val LOW_HERB_COUNT: Int = 10
    private const val HIGH_HERB_COUNT: Int = 5

    private const val BUSH_COUNT: Int = 10

    val REQUIRED_COUNT: Map<String, Int> =
        buildMap {
            put("obj.barley_seed", HOP_COUNT)
            put("obj.hammerstone_hop_seed", HOP_COUNT)
            put("obj.asgarnian_hop_seed", HOP_COUNT)
            put("obj.jute_seed", HOP_COUNT)
            put("obj.yanillian_hop_seed", HOP_COUNT)
            put("obj.krandorian_hop_seed", HOP_COUNT)
            put("obj.wildblood_hop_seed", WILDBLOOD_HOP_COUNT)
            put("obj.potato_seed", ALLOTMENT_COUNT)
            put("obj.onion_seed", ALLOTMENT_COUNT)
            put("obj.cabbage_seed", ALLOTMENT_COUNT)
            put("obj.tomato_seed", ALLOTMENT_COUNT)
            put("obj.sweetcorn_seed", ALLOTMENT_COUNT)
            put("obj.strawberry_seed", ALLOTMENT_COUNT)
            put("obj.watermelon_seed", ALLOTMENT_COUNT)
            put("obj.snape_grass_seed", ALLOTMENT_COUNT)
            put("obj.marigold_seed", FLOWER_COUNT)
            put("obj.rosemary_seed", FLOWER_COUNT)
            put("obj.nasturtium_seed", FLOWER_COUNT)
            put("obj.woad_seed", FLOWER_COUNT)
            put("obj.limpwurt_seed", FLOWER_COUNT)
            put("obj.white_lily_seed", FLOWER_COUNT)
            put("obj.guam_seed", LOW_HERB_COUNT)
            put("obj.marrentill_seed", LOW_HERB_COUNT)
            put("obj.tarromin_seed", LOW_HERB_COUNT)
            put("obj.harralander_seed", LOW_HERB_COUNT)
            put("obj.ranarr_seed", HIGH_HERB_COUNT)
            put("obj.toadflax_seed", HIGH_HERB_COUNT)
            put("obj.irit_seed", HIGH_HERB_COUNT)
            put("obj.avantoe_seed", HIGH_HERB_COUNT)
            put("obj.kwuarm_seed", HIGH_HERB_COUNT)
            put("obj.snapdragon_seed", HIGH_HERB_COUNT)
            put("obj.cadantine_seed", HIGH_HERB_COUNT)
            put("obj.lantadyme_seed", HIGH_HERB_COUNT)
            put("obj.dwarf_weed_seed", HIGH_HERB_COUNT)
            put("obj.torstol_seed", HIGH_HERB_COUNT)
            put("obj.redberry_bush_seed", BUSH_COUNT)
            put("obj.cadavaberry_bush_seed", BUSH_COUNT)
            put("obj.dwellberry_bush_seed", BUSH_COUNT)
            put("obj.jangerberry_bush_seed", BUSH_COUNT)
            put("obj.whiteberry_bush_seed", BUSH_COUNT)
            put("obj.poisonivy_bush_seed", BUSH_COUNT)
        }

    val ALL: List<String> = REQUIRED_COUNT.keys.toList()
}
