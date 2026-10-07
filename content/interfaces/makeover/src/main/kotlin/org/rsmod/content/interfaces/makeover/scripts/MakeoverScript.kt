package org.rsmod.content.interfaces.makeover.scripts

import jakarta.inject.Inject
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.interfaces.makeover.MakeoverBooth
import org.rsmod.content.interfaces.makeover.MakeoverStyles
import org.rsmod.content.interfaces.makeover.applyMakeoverMage
import org.rsmod.content.interfaces.makeover.confirmMakeover
import org.rsmod.content.interfaces.makeover.makeoverBoothLoop
import org.rsmod.content.interfaces.makeover.navigateMakeover
import org.rsmod.content.interfaces.makeover.revertMakeoverOnClose
import org.rsmod.content.interfaces.makeover.selectMakeoverBodyType
import org.rsmod.content.interfaces.makeover.selectMakeoverColour
import org.rsmod.content.interfaces.makeover.selectMakeoverPronoun
import org.rsmod.content.interfaces.makeover.selectMakeoverSkin
import org.rsmod.content.interfaces.makeover.selectMakeoverStyle
import org.rsmod.content.interfaces.makeover.stopMakeoverBooth
import org.rsmod.game.entity.player.Appearance
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MakeoverScript @Inject constructor(private val worldRepo: WorldRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        MakeoverStyles.load()

        onIfModalButton("component.makeover:item_area") { selectMakeoverStyle(it.comsub) }
        onIfModalButton("component.makeover:colours") { selectMakeoverColour(it.comsub) }
        onIfModalButton("component.makeover:select") { confirmMakeover(close = false) }
        onIfModalButton("component.makeover:select_and_close") { confirmMakeover(close = true) }
        onIfModalButton("component.makeover:burger_menu_frame") { navigateMakeover(it.comsub) }

        onIfModalButton("component.makeover_mage:bodytype_a") {
            selectMakeoverBodyType(Appearance.BODY_TYPE_A)
        }
        onIfModalButton("component.makeover_mage:bodytype_b") {
            selectMakeoverBodyType(Appearance.BODY_TYPE_B)
        }
        onIfModalButton("component.makeover_mage:he_him") {
            selectMakeoverPronoun(Appearance.PRONOUN_HE)
        }
        onIfModalButton("component.makeover_mage:they_them") {
            selectMakeoverPronoun(Appearance.PRONOUN_THEY)
        }
        onIfModalButton("component.makeover_mage:she_her") {
            selectMakeoverPronoun(Appearance.PRONOUN_SHE)
        }
        onIfModalButton("component.makeover_mage:colour") { selectMakeoverSkin(it.comsub) }
        onIfModalButton("component.makeover_mage:apply_button") { applyMakeoverMage(close = false) }
        onIfModalButton("component.makeover_mage:confirm_button") { applyMakeoverMage(close = true) }
        onIfModalButton("component.makeover_mage:burger_menu_frame") { navigateMakeover(it.comsub) }

        onIfClose("interface.makeover") {
            stopMakeoverBooth(player)
            revertMakeoverOnClose(player)
        }
        onIfClose("interface.makeover_mage") {
            stopMakeoverBooth(player)
            revertMakeoverOnClose(player)
        }
        onPlayerLogout {
            stopMakeoverBooth(player)
            revertMakeoverOnClose(player)
        }

        onPlayerSoftTimer(MakeoverBooth.Timer) { player.makeoverBoothLoop(worldRepo) }
    }
}
