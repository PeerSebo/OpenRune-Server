package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.ItemServerTypeCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbException
import dev.openrune.types.dbcol.DbHelper
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.config.refs.params
import org.rsmod.map.CoordGrid

internal object StandardSpellTeleports {
    val entries: List<StandardSpellTeleport> by lazy {
        validate(DbHelper.table("dbtable.standard_spell_teleports").map(::read))
    }

    val home: StandardSpellTeleport by lazy { get("obj.48_home_teleport") }
    val apeAtoll: StandardSpellTeleport by lazy { get("obj.64_ape_atoll_teleport") }

    private fun get(spell: String): StandardSpellTeleport = entries.single { it.spellObj.id == spell.asRSCM() }

    internal fun read(row: DbHelper): StandardSpellTeleport {
        val hasAlternate = row.required("has_alternate", BooleanCodec)
        val alternateDestination = row.optional("alternate_destination", CoordGridCodec)
        val alternateLevel = row.optional("alternate_destination_level", IntCodec)
        val alternateMessage = row.optional("alternate_missing_destination_message", StringCodec)
        require(hasAlternate || (alternateDestination == null && alternateLevel == null && alternateMessage == null)) {
            "Teleport row ${row.id} has alternate fields without an alternate option"
        }
        return StandardSpellTeleport(
            order = row.required("order", IntCodec),
            spellObj = row.required("spell", ItemServerTypeCodec),
            destination = row.optional("destination", CoordGridCodec),
            destinationLevel = row.optional("destination_level", IntCodec),
            alternate = if (hasAlternate) {
                TeleportOption(
                    alternateDestination,
                    alternateLevel,
                    requireNotNull(alternateMessage) { "Teleport row ${row.id} has no alternate message" },
                )
            } else null,
            requiredQuest = row.optional("required_quest", StringCodec),
            lockedMessage = row.required("locked_message", StringCodec),
            missingDestinationMessage = row.required("missing_destination_message", StringCodec),
            isHome = row.required("is_home", BooleanCodec),
            ancientStyle = row.required("ancient_style", BooleanCodec),
        )
    }

    internal fun validate(rows: List<StandardSpellTeleport>): List<StandardSpellTeleport> {
        val ordered = rows.sortedBy { it.order }
        require(ordered.isNotEmpty() && ordered.map { it.order } == ordered.indices.toList()) {
            "Standard spell teleports must have a unique contiguous order"
        }
        require(ordered.distinctBy { it.spellObj.id }.size == ordered.size) {
            "Standard spell teleports must have unique spell objects"
        }
        for (row in ordered) {
            require(row.destinationLevel == null || row.destinationLevel in 0..3)
            require(row.alternate?.destinationLevel == null || row.alternate.destinationLevel in 0..3)
            require(row.requiredQuest == null || row.requiredQuest.matches(Regex("quest_[a-z0-9_]+")))
            require(row.lockedMessage.isNotBlank() && row.missingDestinationMessage.isNotBlank())
            require(row.alternate == null || row.alternate.missingDestinationMessage.isNotBlank())
        }
        return ordered
    }

    private fun <T, R> DbHelper.required(name: String, codec: DbColumnCodec<T, R>): R {
        val column = getColumn("dbcol.standard_spell_teleports:$name")
        require(column.size == 1) { "Teleport row $id column $name must have one value" }
        return column.decodeAt(0, codec)
    }

    private fun <T, R> DbHelper.optional(name: String, codec: DbColumnCodec<T, R>): R? {
        val column = try {
            getColumn("dbcol.standard_spell_teleports:$name")
        } catch (_: DbException.MissingColumn) {
            return null
        }
        require(column.size == 1) { "Teleport row $id column $name must have one value" }
        return column.decodeAt(0, codec)
    }
}

internal data class StandardSpellTeleport(
    val order: Int,
    val spellObj: ItemServerType,
    val destination: CoordGrid?,
    val destinationLevel: Int?,
    val alternate: TeleportOption?,
    val requiredQuest: String?,
    val lockedMessage: String,
    val missingDestinationMessage: String,
    val isHome: Boolean = false,
    val ancientStyle: Boolean = false,
) {
    fun option(op: IfButtonOp): TeleportOption =
        if (op == IfButtonOp.Op2 && alternate != null) alternate
        else TeleportOption(destination, destinationLevel, missingDestinationMessage)

    fun destination(spell: MagicSpell, option: TeleportOption): CoordGrid? {
        val coord = option.destination ?: destination ?: spell.obj.paramOrNull(params.spell_telecoord)
        val level = option.destinationLevel ?: destinationLevel
        return if (coord != null && level != null) coord.copy(level = level) else coord
    }
}

internal data class TeleportOption(
    val destination: CoordGrid?,
    val destinationLevel: Int?,
    val missingDestinationMessage: String,
)
