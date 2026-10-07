package org.rsmod.game.obj

import dev.openrune.types.ItemServerType
import kotlin.contracts.contract

@Deprecated(
    message = "Moved to org.rsmod.game.inv.InvObj. Will be removed before version 1.0.0",
    replaceWith = ReplaceWith("org.rsmod.game.inv.InvObj"),
    level = DeprecationLevel.ERROR,
)
public typealias InvObj = org.rsmod.game.inv.InvObj

@Deprecated(
    message =
        "Import from org.rsmod.game.inv package instead. Will be removed before version 1.0.0",
    level = DeprecationLevel.ERROR,
)
public fun org.rsmod.game.inv.InvObj?.isType(type: ItemServerType): Boolean {
    contract { returns(true) implies (this@isType != null) }
    return this != null && type.id == id
}

@Deprecated(
    message =
        "Import from org.rsmod.game.inv package instead. Will be removed before version 1.0.0",
    level = DeprecationLevel.ERROR,
)
public fun org.rsmod.game.inv.InvObj?.isAnyType(
    type1: ItemServerType,
    type2: ItemServerType,
): Boolean {
    contract { returns(true) implies (this@isAnyType != null) }
    return this != null && (type1.id == id || type2.id == id)
}

@Deprecated(
    message =
        "Import from org.rsmod.game.inv package instead. Will be removed before version 1.0.0",
    level = DeprecationLevel.ERROR,
)
public fun org.rsmod.game.inv.InvObj?.isAnyType(
    type1: ItemServerType,
    type2: ItemServerType,
    type3: ItemServerType,
): Boolean {
    contract { returns(true) implies (this@isAnyType != null) }
    return this != null && (type1.id == id || type2.id == id || type3.id == id)
}

@Deprecated(
    message =
        "Import from org.rsmod.game.inv package instead. Will be removed before version 1.0.0",
    level = DeprecationLevel.ERROR,
)
public fun org.rsmod.game.inv.InvObj?.isAnyType(
    type1: ItemServerType,
    type2: ItemServerType,
    type3: ItemServerType,
    type4: ItemServerType,
): Boolean {
    contract { returns(true) implies (this@isAnyType != null) }
    return this != null && (type1.id == id || type2.id == id || type3.id == id || type4.id == id)
}

@Deprecated(
    message =
        "Import from org.rsmod.game.inv package instead. Will be removed before version 1.0.0",
    level = DeprecationLevel.ERROR,
)
public fun org.rsmod.game.inv.InvObj?.isAnyType(
    type1: ItemServerType,
    type2: ItemServerType,
    vararg types: ItemServerType,
): Boolean {
    contract { returns(true) implies (this@isAnyType != null) }
    return this != null && (type1.id == id || type2.id == id || types.any { it.id == id })
}
