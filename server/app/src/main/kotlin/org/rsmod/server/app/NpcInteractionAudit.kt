package org.rsmod.server.app

import com.google.inject.Injector
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.api.player.events.interact.NpcContentEvents
import org.rsmod.api.player.events.interact.NpcDefaultEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp

internal fun writeNpcInteractionAudit(injector: Injector, output: String) {
    val interactions = injector.getInstance(NpcInteractions::class.java)
    val player = Player()
    val rows = mutableListOf<List<Any>>()
    val seen = mutableSetOf<Pair<Int, Int>>()
    for (npc in injector.getInstance(NpcList::class.java)) {
        var visible = npc.visType
        val visited = mutableSetOf<Int>()
        while (visited.add(visible.id)) {
            visible = interactions.multiNpc(visible, player.vars) ?: break
        }
        if (!seen.add(npc.id to visible.id)) continue
        for (op in InteractionOp.entries) {
            val option = visible.actions.getOpOrNull(op.slot - 1) ?: continue
            if (!option.equals("Talk-to", true) && !option.equals("Trade", true)) continue
            val event = interactions.opTrigger(player, npc, op)
            val approach = interactions.apTrigger(player, npc, op)
            val state = when (event) {
                is NpcEvents.Op -> "explicit"
                is NpcContentEvents.Op -> "content"
                is NpcUnimplementedEvents.Op -> "fallback"
                is NpcDefaultEvents.Op, null -> when (approach) {
                    is NpcEvents.Ap -> "explicit-approach"
                    is NpcContentEvents.Ap -> "content-approach"
                    is NpcUnimplementedEvents.Ap -> "fallback-approach"
                    is NpcDefaultEvents.Ap -> "generic-default"
                    else -> if (event == null) "missing" else "generic-default"
                }
                else -> "other"
            }
            rows += listOf(
                npc.id, RSCM.getReverseMapping(RSCMType.NPC, npc.id), visible.id,
                visible.name, op.slot, option, state, event?.id ?: "",
                npc.coords.x, npc.coords.z, npc.coords.level,
                approach?.javaClass?.name ?: "", approach?.id ?: "",
            )
        }
    }
    val path = Path.of(output)
    path.toAbsolutePath().parent?.let(Files::createDirectories)
    val header = listOf("spawn_type", "symbol", "visible_type", "name", "slot", "option", "handler", "handler_key", "example_x", "example_z", "example_level", "approach_handler", "approach_key")
    Files.newBufferedWriter(path).use { writer ->
        for (row in listOf(header) + rows.sortedWith(compareBy({ it[3].toString() }, { it[0].toString() }, { it[4].toString() }))) {
            writer.appendLine(row.joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" })
        }
    }
    println("NPC INTERACTION AUDIT: ${rows.size} distinct spawned actions in default player state written to $path; registration only, dialogue content and quest branches were not executed.")
}
