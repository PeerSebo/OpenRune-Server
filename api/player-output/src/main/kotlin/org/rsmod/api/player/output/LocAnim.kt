package org.rsmod.api.player.output

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.specific.LocAnimSpecific
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo

public fun Player.locAnimSpecific(loc: BoundLocInfo, sequence: String) {
    val id = sequence.asRSCM(RSCMType.SEQ)
    // This packet treats 65535 as an animation ID, not a reset sentinel.
    require(id in 0 until 0xffff) { "Location animation must have a valid sequence: $sequence" }
    val x = loc.coords.x - buildArea.x
    val z = loc.coords.z - buildArea.z
    if (level != loc.coords.level || x !in 0..103 || z !in 0..103) return
    client.write(LocAnimSpecific(id, x, z, loc.shape.id, loc.angle.id))
}
