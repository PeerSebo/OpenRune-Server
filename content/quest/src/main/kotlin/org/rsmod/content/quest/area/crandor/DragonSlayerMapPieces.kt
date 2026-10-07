package org.rsmod.content.quest.area.crandor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.plugin.scripts.ScriptContext

internal class DragonSlayerMapPieces {

    fun ScriptContext.register() {
        onOpHeld1(DragonSlayerRefs.MapPartMelzar) {
            studyPiece(DragonSlayerRefs.MapPartMelzar, StudyMelzar)
        }
        onOpHeld1(DragonSlayerRefs.MapPartLozar) {
            studyPiece(DragonSlayerRefs.MapPartLozar, StudyLozar)
        }
        onOpHeld1(DragonSlayerRefs.MapPartThalzar) {
            studyPiece(DragonSlayerRefs.MapPartThalzar, StudyThalzar)
        }

        onOpHeld1(DragonSlayerRefs.DragonMap) { studyFinishedMap() }

        onOpHeldU(DragonSlayerRefs.MapPartMelzar, DragonSlayerRefs.MapPartLozar) { combine() }
        onOpHeldU(DragonSlayerRefs.MapPartMelzar, DragonSlayerRefs.MapPartThalzar) { combine() }
        onOpHeldU(DragonSlayerRefs.MapPartLozar, DragonSlayerRefs.MapPartThalzar) { combine() }
    }

    private suspend fun ProtectedAccess.studyPiece(piece: String, text: String) {
        startDialogue { objbox(piece, text) }
    }

    private fun ProtectedAccess.studyFinishedMap() {
        ifOpenMainModal(DragonSlayerRefs.CrandorMapInterface)
    }

    private suspend fun ProtectedAccess.combine() {
        if (!inv.contains(DragonSlayerRefs.MapPartMelzar) ||
            !inv.contains(DragonSlayerRefs.MapPartLozar) ||
            !inv.contains(DragonSlayerRefs.MapPartThalzar)
        ) {
            return
        }
        // One atomic transaction for the three deletes; the insert cannot fail afterwards (the
        // recipe frees three slots and fills one), and if it somehow does the pieces are restored
        // so nothing is eaten.
        val deleted = invDel(inv, mapPartMelzar, 1, mapPartLozar, 1, mapPartThalzar, 1)
        if (deleted.failure) {
            return
        }
        if (invAdd(inv, DragonSlayerRefs.DragonMap, 1).failure) {
            invAdd(inv, DragonSlayerRefs.MapPartMelzar, 1)
            invAdd(inv, DragonSlayerRefs.MapPartLozar, 1)
            invAdd(inv, DragonSlayerRefs.MapPartThalzar, 1)
            return
        }
        startDialogue { objbox(DragonSlayerRefs.DragonMap, CombineText) }
    }

    private val mapPartMelzar: ItemServerType by lazy { resolveObj(DragonSlayerRefs.MapPartMelzar) }

    private val mapPartLozar: ItemServerType by lazy { resolveObj(DragonSlayerRefs.MapPartLozar) }

    private val mapPartThalzar: ItemServerType by lazy {
        resolveObj(DragonSlayerRefs.MapPartThalzar)
    }

    private fun resolveObj(internal: String): ItemServerType =
        ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))
            ?: error("Missing obj type: $internal")

    private companion object {

        private const val StudyMelzar =
            "This is a piece of map that you found in Melzar's Maze. You will need to join it to " +
                "the other two map pieces before you can see the route to Crandor."

        private const val StudyLozar =
            "This is a piece of map that you got from Wormbrain, the goblin thief. You will need " +
                "to join it to the other two map pieces before you can see the route to Crandor."

        private const val StudyThalzar =
            "This is a piece of map that you found in a secret chest in the Dwarven Mine. You " +
                "will need to join it to the other two map pieces before you can see the route " +
                "to Crandor."

        private const val CombineText =
            "You put the three pieces together and assemble a map that shows the route through " +
                "the reefs to Crandor."
    }
}
