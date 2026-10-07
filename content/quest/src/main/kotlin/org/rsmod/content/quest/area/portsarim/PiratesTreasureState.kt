package org.rsmod.content.quest.area.portsarim

import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestVar
import org.rsmod.game.entity.Player

internal object PiratesTreasureState {
    const val QuestKey: String = "quest_piratestreasure"

    const val QuestVarp: String = "varp.hunt"

    const val NotStarted: Int = 0

    const val RumRequested: Int = 1

    const val KeyObtained: Int = 2

    const val MessageRead: Int = 3

    const val Complete: Int = 4

    const val KaramjaRum: String = "obj.karamja_rum"
    const val ChestKey: String = "obj.chest_key"
    const val PirateMessage: String = "obj.piratemessage"
    const val Casket: String = "obj.pirate_casket"
    const val WhiteApron: String = "obj.white_apron"
    const val GroundApron: String = "obj.piratetreasure_apron"
    const val Banana: String = "obj.banana"
    const val Spade: String = "obj.spade"
    const val Coins: String = "obj.coins"

    const val BananaRum: String = "obj.tbwt_banana_in_karamja_rum"
    const val SlicedBananaRum: String = "obj.tbwt_sliced_banana_in_karamja_rum"

    const val TakeFromContainerSeq: String = "seq.human_pickuptable"
    const val PutDownSound: String = "synth.PUT_DOWN"
    const val TakeSound: String = "synth.PICK2"
    const val DigSeq: String = "seq.human_dig"
    const val DigSound: String = "synth.DIGSPADE"
    const val ChestOpenSound: String = "synth.CHEST_OPEN"
    const val LockedSound: String = "synth.LOCKED"

    const val SearchRevealTicks: Int = 4

    const val CrateCapacity: Int = 10

    const val CratePayment: Int = 30

    val LUTHAS_EMPLOYED = QuestVar.boolean("LUTHAS_EMPLOYED", "varp.pirates_treasure_luthas_employed", legacyKey = "pirates_treasure_luthas_employed", migratedVarp = "varp.pirates_treasure_luthas_employed_migrated")

    val CRATE_BANANAS = QuestVar.integer("CRATE_BANANAS", "varp.pirates_treasure_crate_bananas", legacyKey = "pirates_treasure_crate_bananas", migratedVarp = "varp.pirates_treasure_crate_bananas_migrated")

    val CRATE_RUM = QuestVar.boolean("CRATE_RUM", "varp.pirates_treasure_crate_rum", legacyKey = "pirates_treasure_crate_rum", migratedVarp = "varp.pirates_treasure_crate_rum_migrated")

    val WYDIN_EMPLOYED = QuestVar.boolean("WYDIN_EMPLOYED", "varp.pirates_treasure_wydin_employed", legacyKey = "pirates_treasure_wydin_employed", migratedVarp = "varp.pirates_treasure_wydin_employed_migrated")

    fun quest(): Quest =
        checkNotNull(Quest.get(QuestKey)) {
            "Pirate's Treasure is not registered; is PiratesTreasure in the plugin list?"
        }

    fun stage(player: Player): Int = quest().getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= RumRequested

    fun crateBananas(player: Player): Int = CRATE_BANANAS.get(player)

    fun crateHasRum(player: Player): Boolean = CRATE_RUM.get(player)

    fun isLuthasEmployee(player: Player): Boolean = LUTHAS_EMPLOYED.get(player)

    fun isWydinEmployee(player: Player): Boolean = WYDIN_EMPLOYED.get(player)
}
