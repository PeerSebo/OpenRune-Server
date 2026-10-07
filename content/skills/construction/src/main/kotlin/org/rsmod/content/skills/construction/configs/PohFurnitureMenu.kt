package org.rsmod.content.skills.construction.configs

internal object PohFurnitureMenu {
    const val INTERFACE: String = "interface.poh_furniture_creation"

    const val ENTRY_SCRIPT: String = "clientscript.[clientscript,poh_furniture_creation_entry]"

    const val LAYOUT_SCRIPT: String = "clientscript.[clientscript,script1406]"

    const val CONTENTS: String = "component.poh_furniture_creation:contents"

    // Interface 458 has 31 row layers; clientscript 1406 clamps to that limit.
    const val MAX_ROWS: Int = 31

    const val CLIENT_TABLE: Int = 110

    const val COL_NAME: Int = 1

    const val COL_MATERIAL: Int = 2

    const val COL_REQUIREMENT: Int = 3

    // 32923 is the client table's any-nails token, distinct from obj.nails.
    const val ANY_NAILS: String = "obj.any_nails"
}
