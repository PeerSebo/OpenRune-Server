package org.rsmod.content.quest.manager

import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.content.quest.area.corsaircove.CorsairCurseIthoiKillHook
import org.rsmod.content.quest.area.crandor.DragonSlayerElvargKillHook
import org.rsmod.content.quest.area.draynor.VampyreSlayerRegenerateHook
import org.rsmod.content.quest.area.lumbridge.BrutusCombatGate
import org.rsmod.content.quest.area.lumbridge.BullBonesTakeGate
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest
import org.rsmod.content.quest.area.varrock.ARuffOutlawOwnershipGate
import org.rsmod.content.quest.area.varrock.ARuffSituation
import org.rsmod.content.quest.area.varrock.ARuffSituationOutlaws
import org.rsmod.content.quest.area.varrock.DemonSlayerSilverlightGate
import org.rsmod.content.quest.area.varrock.DemonSlayerWizardKillHook
import org.rsmod.content.quest.area.varrock.ShieldOfArravWeaponsmasterKillHook
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        addSetBinding<SpellQuestRequirement>(PolicySpellQuestRequirement::class.java)
        bindInstance<RuneMysteriesQuest>()
        bindInstance<LostCityQuest>()
        bindInstance<GertrudesCatQuest>()
        bindInstance<ARuffSituation>()

        addSetBinding<NpcDeathKillHook>(VampyreSlayerRegenerateHook::class.java)

        addSetBinding<NpcDeathKillHook>(CorsairCurseIthoiKillHook::class.java)

        addSetBinding<NpcDeathKillHook>(DragonSlayerElvargKillHook::class.java)

        addSetBinding<NpcDeathKillHook>(DemonSlayerWizardKillHook::class.java)

        addSetBinding<NpcDeathKillHook>(ShieldOfArravWeaponsmasterKillHook::class.java)
        addSetBinding<NpcDeathKillHook>(ARuffSituationOutlaws::class.java)

        addSetBinding<NpcAttackValidateHook>(DemonSlayerSilverlightGate::class.java)
        addSetBinding<NpcAttackValidateHook>(BrutusCombatGate::class.java)
        addSetBinding<NpcAttackValidateHook>(ARuffOutlawOwnershipGate::class.java)
        addSetBinding<PlayerObjTakeValidateHook>(BullBonesTakeGate::class.java)
    }
}
