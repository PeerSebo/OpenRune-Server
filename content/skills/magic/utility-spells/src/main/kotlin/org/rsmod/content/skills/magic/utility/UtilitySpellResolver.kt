package org.rsmod.content.skills.magic.utility

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.MagicSpellType
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.aliases.ParamInt
import org.rsmod.api.config.aliases.ParamObj
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.spells.MagicSpellRegistry

internal fun MagicSpellRegistry.resolveStandardSpell(spellObj: String): MagicSpell? {
    val type = ServerCacheManager.getItem(spellObj.asRSCM(RSCMType.OBJ)) ?: return null
    return getObjSpell(type) ?: type.toStandardSpell()
}

private fun ItemServerType.toStandardSpell(): MagicSpell? {
    val button = paramOrNull(BaseParams.spell_button) ?: return null
    val castXpTenths = paramOrNull(BaseParams.spell_castxp) ?: return null

    val objReqs = buildList {
        fun addRequirement(objParam: ParamObj, countParam: ParamInt) {
            val paramObj = paramOrNull(objParam) ?: return
            val worn = paramObj.wearpos1.takeIf { it != -1 }
            this += MagicSpell.ObjRequirement(paramObj, param(countParam), worn)
        }
        addRequirement(BaseParams.spell_runetype_1, BaseParams.spell_runecount_1)
        addRequirement(BaseParams.spell_runetype_2, BaseParams.spell_runecount_2)
        addRequirement(BaseParams.spell_runetype_3, BaseParams.spell_runecount_3)
        addRequirement(BaseParams.spell_runetype_4, BaseParams.spell_runecount_4)
    }

    return MagicSpell(
        obj = this,
        name = param(BaseParams.spell_name),
        component = button,
        spellbook = Spellbook[param(BaseParams.spell_spellbook)],
        type = MagicSpellType[param(BaseParams.spell_type)] ?: MagicSpellType.Utility,
        maxHit = param(BaseParams.spell_maxhit),
        levelReq = param(BaseParams.spell_levelreq),
        castXp = castXpTenths / 10.0,
        objReqs = objReqs,
    )
}
