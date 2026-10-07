package org.rsmod.content.quest.area.crandor

import jakarta.inject.Inject
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

// Write dragonquestvar flags individually; a whole-varp write would overwrite sibling fields.
class DragonSlayer
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val musicPlayer: MusicPlayer,
    private val shops: Shops,
) :
    QuestScript(
        DragonSlayerRefs.QuestKey,
        DragonSlayerRefs.QuestVarp,
        rewards {
            xp("stat.strength", XpReward)
            xp("stat.defence", XpReward)
            extra("Ability to equip Rune and")
            extra("Dragon Platebodies")
        },
        completedQuestItemDisplay =
            ItemRewardDisplay(DragonSlayerRefs.ElvargHead, x = ScrollModelX, y = ScrollModelY),
        completionJingle = DragonSlayerRefs.CompletionJingle,
    ) {

    private val cutscene =
        DragonSlayerCutscene(
            quest,
            regionRepo,
            npcRepo,
            objRepo,
            locRepo,
            worldRepo,
            musicPlayer,
        )

    private val dialogue = DragonSlayerDialogue(quest, cutscene, shops)

    private val scenery = DragonSlayerScenery(quest, cutscene, locRepo, worldRepo)

    private val mapPieces = DragonSlayerMapPieces()

    private var Player.briefed by intVarBit("varbit.dragonslayer_champion_explained_elvarg")
    private var Player.instructionsMelzar by intVarBit("varbit.dragonslayer_instructions_melzar")
    private var Player.instructionsOracle by intVarBit("varbit.dragonslayer_instructions_oracle")
    private var Player.instructionsWormbrain by
        intVarBit("varbit.dragonslayer_instructions_wormbrain")
    private var Player.hullFixed by intVarBit("varbit.dragonslayer_ship_fullyfixed")
    private var Player.nedAgreed by intVarBit("varbit.dragonslayer_asked_ned")

    override fun ScriptContext.init() {
        check(quest.maxSteps == DragonSlayerStage.Complete) {
            "Dragon Slayer I end state is ${quest.maxSteps} in the cache dbrow, but " +
                "DragonSlayerStage.Complete is ${DragonSlayerStage.Complete}."
        }

        assertDragonSlayerSceneCoords()

        for (track in
            listOf(
                DragonSlayerRefs.MusicCrestOfAWave,
                DragonSlayerRefs.MusicEyeOfTheStorm,
                DragonSlayerRefs.MusicTheShadow,
                DragonSlayerRefs.MusicAttack2,
                DragonSlayerRefs.MusicDangerousRoad,
            )) {
            checkNotNull(
                org.rsmod.content.quest.area.icemountain.musicRow(track)
            ) {
                "No music row named '$track'."
            }
        }

        with(dialogue) { register() }
        with(scenery) { register() }
        with(mapPieces) { register() }
        with(cutscene) { register() }
    }

    fun nedCrandorOption(player: Player): String? = dialogue.nedCrandorOption(player)

    suspend fun nedCrandorBranch(dialogue: org.rsmod.api.player.dialogue.Dialogue) {
        this.dialogue.nedCrandorBranch(dialogue)
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Guildmaster<col=000080> in the " +
            "<col=800000>Champions' Guild<col=000080>, south of <col=800000>Varrock<col=000080>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            strike(GuildmasterSaid)

            if (stage >= DragonSlayerStage.OziachSpoken) {
                strike(OziachSaid)
            } else {
                line(SpeakToOziach)
                return@questJournal
            }

            if (player.player.briefed == 0) {
                line(ReturnToGuildmaster)
                return@questJournal
            }

            strike(GuildmasterInstructed)
            mapPieceBlock(player)
            shieldBlock(player)
            shipBlock(player, stage)
            objectiveBlock(stage)
        }

    private fun QuestJournalBuilder.mapPieceBlock(player: ProtectedAccess) {
        if (player.inv.contains(DragonSlayerRefs.MapPartMelzar) ||
            player.inv.contains(DragonSlayerRefs.DragonMap)) {
            strike(FoundMelzarPiece)
        } else if (player.player.instructionsMelzar == 0) {
            line(MelzarPieceHint)
        }

        if (player.inv.contains(DragonSlayerRefs.MapPartThalzar) ||
            player.inv.contains(DragonSlayerRefs.DragonMap)) {
            strike(FoundThalzarPiece)
        } else if (player.player.instructionsOracle == 0) {
            line(ThalzarPieceHint)
        }

        if (player.inv.contains(DragonSlayerRefs.MapPartLozar) ||
            player.inv.contains(DragonSlayerRefs.DragonMap)) {
            strike(FoundLozarPiece)
        } else if (player.player.instructionsWormbrain == 0) {
            line(LozarPieceHint)
        }
    }

    private fun QuestJournalBuilder.shieldBlock(player: ProtectedAccess) {
        if (player.inv.contains(DragonSlayerRefs.AntiDragonShield)) {
            strike(GotShield)
        } else {
            line(ShieldHint)
        }
    }

    private fun QuestJournalBuilder.shipBlock(player: ProtectedAccess, stage: Int) {
        if (stage >= DragonSlayerStage.ShipBought) {
            strike(BoughtShip)
        } else {
            line(ShipHint)
        }

        if (player.player.hullFixed == 1) {
            strike(RepairedShip)
        } else if (stage >= DragonSlayerStage.ShipBought) {
            line(RepairHint)
        }

        if (player.player.nedAgreed == 1) {
            strike(NedAgreed)
        }
    }

    private fun QuestJournalBuilder.objectiveBlock(stage: Int) {
        when {
            stage >= DragonSlayerStage.ElvargSlain -> line(TellOziach)
            stage >= DragonSlayerStage.Shipwrecked -> line(KillTheDragon)
            stage >= DragonSlayerStage.MapGivenToNed -> line(SetSail)
            else -> {}
        }
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(GuildmasterSaid)
            line(OziachSaid)
            line(GuildmasterInstructed)
            line(FoundMelzarPiece)
            line(FoundThalzarPiece)
            line(FoundLozarPiece)
            line(GotShield)
            line(BoughtShip)
            line(RepairedShip)
            line(NedAgreed)
            line(SlewTheDragon)
        }

    private companion object {
        private const val XpReward: Double = 18_650.0

        private const val ScrollModelX = 75

        private const val ScrollModelY = 125

        private const val GuildmasterSaid =
            "The Guildmaster of the Champions' Guild said I could earn the right to wear rune " +
                "armour if I went on a quest for Oziach, who makes the armour."

        private const val SpeakToOziach =
            "I should speak to Oziach, who lives by the cliffs to the north-west of Edgeville."

        private const val OziachSaid =
            "I spoke to Oziach in Edgeville. He told me to slay the dragon of Crandor island."

        private const val ReturnToGuildmaster =
            "I should return to the Champions' Guild Guildmaster for more detailed instructions."

        private const val GuildmasterInstructed =
            "The Champions' Guild Guildmaster told me I had to find three pieces of a map to " +
                "Crandor, a ship, a captain to take me there and a shield to protect me from the " +
                "dragon's breath."

        private const val MelzarPieceHint =
            "One-third of the map is in Melzar's Maze, north-west of Rimmington."

        private const val ThalzarPieceHint =
            "One-third of the map is hidden, and only the Oracle on Ice Mountain will know where " +
                "it is."

        private const val LozarPieceHint =
            "One-third of the map was stolen by a goblin from the Goblin Village."

        private const val FoundMelzarPiece =
            "I found the piece of the map that was hidden in Melzar's Maze."

        private const val FoundThalzarPiece =
            "I found the piece of the map that was hidden beneath Ice Mountain."

        private const val FoundLozarPiece =
            "I found the piece of the map that the goblin, Wormbrain, stole."

        private const val ShieldHint =
            "I should ask the Duke of Lumbridge for an anti-dragonbreath shield."

        private const val GotShield =
            "The Duke of Lumbridge gave me an anti-dragonbreath shield."

        private const val ShipHint =
            "I should see if there is a ship for sale in Port Sarim. I should check around the " +
                "docks."

        private const val BoughtShip = "I bought a ship in Port Sarim called the Lady Lumbridge."

        private const val RepairHint =
            "My ship will need some repairs, using wooden planks and steel nails, before it is " +
                "seaworthy again."

        private const val RepairedShip =
            "I have repaired my ship using wooden planks and steel nails."

        private const val NedAgreed =
            "Captain Ned from Draynor Village has agreed to sail the ship to Crandor for me."

        private const val SetSail =
            "Now I should go to my ship in Port Sarim and set sail for Crandor!"

        private const val KillTheDragon =
            "Now all I need to do is kill the dragon! I should search under Crandor for the dragon."

        private const val TellOziach = "I have slain the dragon! Now I just need to tell Oziach."

        private const val SlewTheDragon =
            "I slew the dragon of Crandor and took its head to Oziach, who finally agreed to sell " +
                "me a rune platebody."
    }
}
