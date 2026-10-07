package org.rsmod.content.areas.misc.goblinvillage

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.misc.goblinvillage.configs.GoblinSide
import org.rsmod.content.areas.misc.goblinvillage.configs.GoblinSoldiers
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GoblinVillageGoblins @Inject constructor(private val random: GameRandom) : PluginScript() {
    private val sides = HashMap<Int, GoblinSide>()

    private val Player.goblinDiplomacyStage: Int
        get() = vars[GoblinDiplomacyStageVarbit]

    override fun ScriptContext.startup() {
        for (soldier in GoblinSoldiers.All) {
            sides[soldier.npc.asRSCM(RSCMType.NPC)] = soldier.side
            onOpNpc1(soldier.npc) { talkTo(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.talkTo(goblin: Npc) {
        val side = sides[goblin.id] ?: return
        startDialogue(goblin) { goblinChat(goblin, side) }
    }

    private suspend fun Dialogue.goblinChat(goblin: Npc, side: GoblinSide) {
        val settled = player.goblinDiplomacyStage >= GoblinDiplomacyComplete
        val pool = if (settled) 5 else 4
        when (random.of(maxExclusive = pool)) {
            0 -> killYouHuman(goblin)
            1 -> goAwaySmellyHuman(goblin)
            2 -> whatYouDoingHere(goblin)
            3 -> happyGoblinNewCentury(goblin)
            else -> brownArmourBest(side)
        }
    }

    private suspend fun Dialogue.killYouHuman(goblin: Npc) {
        chatNpc(angry, "I kill you human!")
        goblin.attack(player)
    }

    private suspend fun Dialogue.goAwaySmellyHuman(goblin: Npc) {
        chatNpc(angry, "Go away smelly human!")
        goblin.attack(player)
    }

    private suspend fun Dialogue.whatYouDoingHere(goblin: Npc) {
        chatNpc(quiz, "What you doing here?")
        val option = choice2("I'm here to kill all you goblins!", 1, "I'm just looking around.", 2)
        when (option) {
            1 -> {
                chatPlayer(angry, "I'm here to kill all you goblins!")
                chatNpc(angry, "I kill you!")
                goblin.attack(player)
            }
            2 -> {
                chatPlayer(neutral, "I'm just looking around.")
                chatNpc(neutral, "Me not sure that allowed. You have to check with generals.")
            }
        }
    }

    private suspend fun Dialogue.happyGoblinNewCentury(goblin: Npc) {
        chatNpc(happy, "Happy goblin new century!")
        val option = choice2("Happy new century!", 1, "What is the goblin new century?", 2)
        if (option == 1) {
            chatPlayer(happy, "Happy new century!")
            return
        }

        chatPlayer(quiz, "What is the goblin new century?")
        chatNpc(
            neutral,
            "Goblin century mark year of big battle on Plain of Mud. " +
                "That when Big High War God give us commandments.",
        )

        val topic =
            choice4(
                "Who is the Big High War God?",
                1,
                "What are the goblin commandments?",
                2,
                "Where is the Plain of Mud?",
                3,
                "I need to go.",
                4,
            )
        when (topic) {
            1 -> {
                chatPlayer(quiz, "Who is the Big High War God?")
                chatNpc(
                    happy,
                    "Big High War God take goblins and make them strong! Without him, we small " +
                        "and weak and stupid. But thanks to Big High War God, we most powerful " +
                        "race in Gielinor!",
                )
                chatPlayer(neutral, "Umm... you're clearly not the most powerful race in Gielinor.")
                chatNpc(angry, "Not to doubt word of Big High War God! I kill you!")
                goblin.attack(player)
            }
            2 -> {
                chatPlayer(quiz, "What are the goblin commandments?")
                chatNpc(
                    neutral,
                    "Slay enemies of Big High War God! Not show mercy! Not run from battle! " +
                        "Not doubt word of Big High War God!",
                )
                chatNpc(
                    angry,
                    "Without commandments we not know right from wrong. " +
                        "We be like other races who not know war is good!",
                )
                goblin.attack(player)
            }
            3 -> {
                chatPlayer(quiz, "Where is the Plain of Mud?")
                chatNpc(angry, "That goblin secret! No human ever find Plain of Mud!")
                goblin.attack(player)
            }
            4 -> chatPlayer(neutral, "I need to go.")
        }
    }

    private suspend fun Dialogue.brownArmourBest(side: GoblinSide) {
        chatNpc(happy, "Brown armour best!")

        val stillWearingLabel =
            when (side) {
                GoblinSide.Red -> "But you're still wearing red!"
                GoblinSide.Green -> "But you're still wearing green!"
            }

        val option = choice3("Why is brown best?", 1, stillWearingLabel, 2, "Err, okay.", 3)
        when (option) {
            1 -> {
                chatPlayer(quiz, "Why is brown best?")
                chatNpc(
                    happy,
                    "General Wartface and General Bentnoze both say it is. " +
                        "And normally they never agree!",
                )
            }
            2 ->
                when (side) {
                    GoblinSide.Red -> {
                        chatPlayer(quiz, "But you're still wearing red armour!")
                        chatNpc(worried, "It not red, it just red-ish brown!")
                    }
                    GoblinSide.Green -> {
                        chatPlayer(quiz, "But you're still wearing green armour!")
                        chatNpc(worried, "Um... my brown armour getting wash.")
                    }
                }
            3 -> chatPlayer(neutral, "Err, okay.")
        }
    }

    private fun Npc.attack(player: Player) {
        queueCombatRetaliate(player)
    }

    private companion object {
        const val GoblinDiplomacyStageVarbit: String = "varbit.gobdip_main"

        const val GoblinDiplomacyComplete: Int = 6
    }
}
