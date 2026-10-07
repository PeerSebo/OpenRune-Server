package org.rsmod.content.drops

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathDropContext
import org.rsmod.api.death.NpcDeathDropHook

@Singleton
public class NoRemainsDeathDropHook @Inject constructor() : NpcDeathDropHook {

    override fun tryConsume(context: NpcDeathDropContext): Boolean =
        NO_REMAINS.any(context.npc::isType)

    private companion object {
        private val NO_REMAINS =
            listOf(
                "npc.rat",
                "npc.rat_indoors",
                "npc.spider",
                "npc.sos_pest_spider",
                "npc.upass_little_spider",
                "npc.cowboss",
                "npc.cowboss_routefind",
                "npc.duke_sucellus_asleep",
                "npc.duke_sucellus_asleep_quest",
                "npc.duke_sucellus_awake",
                "npc.duke_sucellus_awake_quest",
                "npc.duke_sucellus_dead",
                "npc.duke_sucellus_dead_quest",
                "npc.duke_sucellus_inactive",
                "npc.duke_sucellus_inactive_quest",
            )
    }
}
