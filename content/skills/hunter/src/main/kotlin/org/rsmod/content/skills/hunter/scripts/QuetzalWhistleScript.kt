package org.rsmod.content.skills.hunter.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.*
import org.rsmod.content.skills.hunter.HunterRumours
import org.rsmod.content.skills.hunter.QuetzalWhistleService
import org.rsmod.content.skills.hunter.configs.QuetzalDestination
import org.rsmod.content.skills.hunter.configs.QuetzalWhistle
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class QuetzalWhistleScript @Inject constructor(
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(QuetzalWhistleService.PITRI) { pitri(it.npc) }
        onOpNpc3(QuetzalWhistleService.PITRI) { QuetzalWhistleService.recharge(player) }
        for (tier in QuetzalWhistle.entries) {
            onOpNpcU(QuetzalWhistleService.PITRI, tier.obj) { pitri(it.npc) }
            onOpHeld1(tier.obj) { if (it.inventory === player.inv) signal(it.slot) }
            if (tier != QuetzalWhistle.INFINITE) onOpHeld2(tier.obj) {
                val item = it.inventory[it.slot] ?: return@onOpHeld2
                mes("Your quetzal whistle has ${tier.charges(item)} charges remaining.")
            }
            onOpHeld3(tier.obj) { mes(HunterRumours.currentName(player)?.let { prey -> "Your current rumour target is $prey." } ?: "You do not currently have a Hunter Rumour.") }
            onOpHeld4(tier.obj) {
                if (it.inventory !== player.inv) return@onOpHeld4
                val selection = QuetzalWhistleService.selection(player, it.slot) ?: return@onOpHeld4
                travel(selection, QuetzalDestination.fromNative(player.vars["varbit.quetzal_last_destination"]) ?: QuetzalDestination.GUILD)
            }
            val blueprint = tier.blueprint ?: continue
            for (obj in if (tier == QuetzalWhistle.BASIC) listOf(blueprint) else listOf(blueprint, "${blueprint}_torn")) {
                onOpHeld1(obj) { mes("Craft this blueprint with a knife, ${tier.logs?.removePrefix("obj.")?.replace('_', ' ')}${if (tier.previous != null) " and the previous tier of quetzal whistle" else ""}.") }
                onOpHeld2(obj) { if (it.inventory === player.inv) QuetzalWhistleService.craft(player, obj) }
            }
        }
        onIfModalButton("component.quetzalwhistle_menu:icons") {
            if (it.op != IfButtonOp.Op1) return@onIfModalButton
            val selection = player.attr[QuetzalWhistleService.MENU] ?: return@onIfModalButton
            val destination = QuetzalDestination.fromButton(it.comsub) ?: return@onIfModalButton
            travel(selection, destination)
        }
        onIfClose("interface.quetzalwhistle_menu") { player.attr.remove(QuetzalWhistleService.MENU) }
        onPlayerLogout { player.attr.remove(QuetzalWhistleService.MENU) }
        for (site in QuetzalDestination.entries.filter { !it.built }) {
            onOpLoc1(site.parent) { QuetzalWhistleService.buildLanding(player, site) }
        }
    }

    private suspend fun ProtectedAccess.pitri(npc: Npc) {
        var option = 5
        startDialogue(npc) {
            chatNpc(happy, "I can help with quetzal whistles. Feeding the birds builds towards an unlimited whistle: ${QuetzalWhistleService.feedCredit(player)} of 10,000 charges so far.")
            option = choice5("I'd like a basic blueprint.", 1, "Recharge my whistles.", 2, "Contribute food to imbue my perfected whistle.", 3, "Reclaim a perfected whistle.", 4, "Nothing, thanks.", 5)
        }
        when (option) {
            1 -> QuetzalWhistleService.blueprint(player)
            2 -> QuetzalWhistleService.recharge(player)
            3 -> QuetzalWhistleService.imbue(player)
            4 -> QuetzalWhistleService.reclaim(player)
        }
    }

    private suspend fun ProtectedAccess.signal(slot: Int) {
        val selection = QuetzalWhistleService.selection(player, slot) ?: return
        if (player.vars["varbit.settings_quetzalwhistle_default_tp"] != 0) {
            travel(selection, QuetzalDestination.GUILD)
            return
        }
        ifOpenMain("interface.quetzalwhistle_menu")
        player.attr[QuetzalWhistleService.MENU] = selection
        // Native on-load builds clickable DB-row children followed by nonclickable lock overlays.
        ifSetEvents("component.quetzalwhistle_menu:icons", 0..13, IfEvent.Op1)
    }

    internal suspend fun ProtectedAccess.travel(selection: QuetzalWhistleService.Selection, destination: QuetzalDestination) {
        if (!QuetzalWhistleService.valid(player, selection)) return
        if (!destination.unlocked(player)) { mes("You must build that landing site before using it."); return }
        if (coords == destination.coords) { mes("You are already at that landing site."); return }
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) { mes(denial); return }
        ifClose()
        player.abortRoute()
        anim("seq.human_quetzal_whistle")
        delay(3)
        if (!QuetzalWhistleService.valid(player, selection) || !destination.unlocked(player)) { resetAnim(); return }
        val from = coords
        telejump(destination.coords, TeleportType.Standard)
        resetAnim()
        if (coords == destination.coords && coords != from) {
            QuetzalWhistleService.spend(player, selection)
            VarPlayerIntMapSetter.set(player, "varbit.quetzal_last_destination", destination.nativeId)
        }
    }
}
