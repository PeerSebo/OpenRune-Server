package org.rsmod.content.skills.hunter.configs

object NettingParams {

    const val BUTTERFLY_NET: String = "obj.hunting_butterfly_net"

    const val MAGIC_NET: String = "obj.ii_magic_butterfly_net"

    val NETS: List<String> = listOf(BUTTERFLY_NET, MAGIC_NET)

    const val MAGIC_NET_BONUS: Int = 20

    const val BUTTERFLY_JAR: String = "obj.butterfly_jar"

    const val IMPLING_JAR: String = "obj.ii_impling_jar"

    fun emptyJar(kind: NetPreyKind): String =
        when (kind) {
            NetPreyKind.BUTTERFLY -> BUTTERFLY_JAR
            NetPreyKind.IMPLING -> IMPLING_JAR
        }

    const val CATCH_QUEUE: String = "queue.hunter_net_catch"

    const val BUTTERFLY_ANIM: String = "seq.human_butterflynet_swing"

    const val IMPLING_ANIM: String = "seq.hunting_impling_catch"

    fun anim(kind: NetPreyKind): String =
        when (kind) {
            NetPreyKind.BUTTERFLY -> BUTTERFLY_ANIM
            NetPreyKind.IMPLING -> IMPLING_ANIM
        }

    const val SWING_SYNTH: String = "synth.HUNTING_BUTTERFLYNET"

    const val BUTTERFLY_CATCH_TICKS: Int = 2

    const val IMPLING_CATCH_TICKS: Int = 4

    fun catchTicks(kind: NetPreyKind): Int =
        when (kind) {
            NetPreyKind.BUTTERFLY -> BUTTERFLY_CATCH_TICKS
            NetPreyKind.IMPLING -> IMPLING_CATCH_TICKS
        }

    // Despawn through NpcRepository so respawn is scheduled; registry deletion is permanent.
    const val BUTTERFLY_RESPAWN_TICKS: Int = 50

    const val IMPLING_RESPAWN_TICKS: Int = 200

    fun respawnTicks(kind: NetPreyKind): Int =
        when (kind) {
            NetPreyKind.BUTTERFLY -> BUTTERFLY_RESPAWN_TICKS
            NetPreyKind.IMPLING -> IMPLING_RESPAWN_TICKS
        }
}
