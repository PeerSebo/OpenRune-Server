package org.rsmod.content.other.pets.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class PetsPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(
            PetsTable.pets(),
            DogsTable.dogs(),
            PetMorphsTable.morphs(),
            PetDropsTable.drops(),
            PetSkillingTable.skilling(),
        )
}
