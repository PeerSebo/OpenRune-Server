package org.rsmod.api.script

import org.rsmod.api.player.events.interact.WorldEntityEvents
import org.rsmod.api.player.events.interact.WorldEntityTEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.plugin.scripts.ScriptContext

/*
 * World entity ops are keyed on the cache `worldentity` config id (`WorldEntityType` id).
 *
 * There is deliberately no `internal: String` / `asRSCM(RSCMType.WORLDENTITY)` overload: there is
 * no `worldentity` table in `.data/gamevals/` and no `worldentity` entry in
 * `.data/gamevals-binary/max-ids.toml`, so a symbol string cannot resolve. Content must name the
 * ids in its own constants, citing `./gradlew :or-cache:dumpWorldEntities`. Adding a `worldentity`
 * gameval table is a separate or-cache change.
 */

public fun ScriptContext.onOpWorldEntity1(
    configId: Int,
    action: suspend ProtectedAccess.(WorldEntityEvents.Op1) -> Unit,
): Unit = onProtectedEvent(configId, action)

public fun ScriptContext.onOpWorldEntity2(
    configId: Int,
    action: suspend ProtectedAccess.(WorldEntityEvents.Op2) -> Unit,
): Unit = onProtectedEvent(configId, action)

public fun ScriptContext.onOpWorldEntity3(
    configId: Int,
    action: suspend ProtectedAccess.(WorldEntityEvents.Op3) -> Unit,
): Unit = onProtectedEvent(configId, action)

public fun ScriptContext.onOpWorldEntity4(
    configId: Int,
    action: suspend ProtectedAccess.(WorldEntityEvents.Op4) -> Unit,
): Unit = onProtectedEvent(configId, action)

public fun ScriptContext.onOpWorldEntity5(
    configId: Int,
    action: suspend ProtectedAccess.(WorldEntityEvents.Op5) -> Unit,
): Unit = onProtectedEvent(configId, action)

public fun ScriptContext.onOpWorldEntityT(
    configId: Int,
    action: suspend ProtectedAccess.(WorldEntityTEvents.Op) -> Unit,
): Unit = onProtectedEvent(configId, action)
