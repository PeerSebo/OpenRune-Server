package org.rsmod.content.interfaces.makeover

import dev.openrune.definition.type.widget.IfEvent
import java.util.Collections
import java.util.WeakHashMap
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance

private const val MAKEOVER_INTERFACE = MakeoverPage.MAKEOVER_INTERFACE
private const val ITEM_AREA = "component.makeover:item_area"
private const val COLOURS = "component.makeover:colours"
private const val SELECT = "component.makeover:select"
private const val SELECT_AND_CLOSE = "component.makeover:select_and_close"
private const val BURGER_MENU = "component.makeover:burger_menu_frame"

private const val MAKEOVER_MAGE_INTERFACE = MakeoverPage.MAKEOVER_MAGE_INTERFACE
private const val MAGE_SKIN_COLOURS = "component.makeover_mage:colour"
private const val MAGE_BURGER_MENU = "component.makeover_mage:burger_menu_frame"
private const val MAGE_CONFIRM_LABEL = "component.makeover_mage:confirm"
private const val MAGE_APPLY_LABEL = "component.makeover_mage:apply"

// Varp 3787 packs page bits 0-4 and body type bit 5; whole-varp writes would clobber the other field.
private var Player.makeoverPageVar: Int by intVarBit("varbit.makeover_interface_type")

private var Player.makeoverBodyTypeVar: Int by intVarBit("varbit.makeover_bodytype")

private var Player.makeoverStyleVar: Int by intVarp("varp.makeover_hair")

private var Player.makeoverColourVar: Int by intVarp("varp.makeover_colour")

private var Player.makeoverMageBodyTypeVar: Int by intVarp("varp.if1")

private var Player.makeoverMageSkinVar: Int by intVarp("varp.if2")

private var Player.makeoverMagePronounVar: Int by intVarp("varp.if3")

// Persist committed pronouns in the varbit; the appearance field alone is transient.
private var Player.committedPronounVar: Int by intVarBit("varbit.settings_transmit_pronouns")

private const val SKIN_COLOUR_SLOT: Int = 4

// Skin swatch indices are not identity-mapped colour IDs.
private val MAGE_SKIN_PALETTE: IntArray = intArrayOf(7, 0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12)

internal class MakeoverSession(private val identKit: IntArray, private val colours: IntArray) {
    var page: MakeoverPage = MakeoverPage.Hair
    var navigating: Boolean = false

    fun isDirty(player: Player): Boolean {
        val currentKit = player.appearance.identKitSnapshot()
        val currentColours = player.appearance.coloursSnapshot()
        for (i in identKit.indices) {
            if (currentKit.getOrNull(i)?.toInt() != identKit[i]) {
                return true
            }
        }
        for (i in colours.indices) {
            if (currentColours.getOrNull(i)?.toInt()?.and(0xFF) != colours[i]) {
                return true
            }
        }
        return false
    }

    fun commit(player: Player) {
        val currentKit = player.appearance.identKitSnapshot()
        for (i in identKit.indices) {
            identKit[i] = currentKit.getOrNull(i)?.toInt() ?: identKit[i]
        }
        val currentColours = player.appearance.coloursSnapshot()
        for (i in colours.indices) {
            colours[i] = currentColours.getOrNull(i)?.toInt()?.and(0xFF) ?: colours[i]
        }
    }

    fun restore(player: Player) {
        for (i in identKit.indices) {
            // A negative snapshot means the slot was never assigned; `setIdentKit` rejects it and
            // there is nothing sensible to put back, so leave the slot as-is.
            if (identKit[i] >= 0) {
                player.appearance.setIdentKit(i, identKit[i])
            }
        }
        for (i in colours.indices) {
            player.appearance.setColour(i, colours[i])
        }
        player.rebuildAppearance()
    }

    internal companion object {
        fun snapshot(player: Player): MakeoverSession {
            val kit = player.appearance.identKitSnapshot().map { it.toInt() }.toIntArray()
            val colours = player.appearance.coloursSnapshot().map { it.toInt() and 0xFF }.toIntArray()
            return MakeoverSession(kit, colours)
        }
    }
}

internal object MakeoverSessions {
    private val sessions: MutableMap<Player, MakeoverSession> =
        Collections.synchronizedMap(WeakHashMap())

    fun open(player: Player): MakeoverSession {
        val session = MakeoverSession.snapshot(player)
        sessions[player] = session
        return session
    }

    fun current(player: Player): MakeoverSession? = sessions[player]

    fun discard(player: Player) {
        sessions.remove(player)
    }
}

internal fun ProtectedAccess.openMakeover(page: MakeoverPage) {
    if (page != MakeoverPage.Body && (MakeoverStyles.tableFor(page)?.size ?: 0) == 0) {
        mes("You can't seem to change that right now.")
        return
    }
    // Retire the old session before snapshotting; modal-close dispatch would otherwise discard the new session.
    val existing = MakeoverSessions.current(player)
    if (existing != null) {
        ifCloseSub(existing.page.interfaceName)
        revertMakeoverOnClose(player)
    }
    val session = MakeoverSessions.open(player)
    showMakeoverPage(session, page)
}

internal fun ProtectedAccess.showMakeoverPage(session: MakeoverSession, page: MakeoverPage) {
    session.page = page
    if (page == MakeoverPage.Body) {
        showMakeoverMagePage()
        return
    }

    if (player.makeoverColourVar != 0) {
        player.makeoverColourVar = 0
    }

    val bodyType = player.appearance.bodyType
    if (player.makeoverBodyTypeVar != bodyType) {
        player.makeoverBodyTypeVar = bodyType
    }
    player.makeoverPageVar = page.varbitValue

    player.makeoverStyleVar = currentStyleIndex(page)

    ifOpenMainModal(MAKEOVER_INTERFACE, colour = -1, transparency = -2)

    ifSetEvents(ITEM_AREA, 0..page.itemAreaEnd, IfEvent.Op1)
    page.colourEnd?.let { ifSetEvents(COLOURS, 0..it, IfEvent.Op1) }
    ifSetEvents(SELECT, MakeoverPage.BUTTON_RANGE, IfEvent.Op1)
    ifSetEvents(SELECT_AND_CLOSE, MakeoverPage.BUTTON_RANGE, IfEvent.Op1)
    ifSetEvents(BURGER_MENU, MakeoverPage.NAV_RANGE, IfEvent.Op1)

    // Arm after opening the modal; its synchronous close event clears the previous timer.
    startMakeoverBooth()
}

private fun ProtectedAccess.showMakeoverMagePage() {
    val appearance = player.appearance
    val bodyType = appearance.bodyType
    if (player.makeoverBodyTypeVar != bodyType) {
        player.makeoverBodyTypeVar = bodyType
    }
    player.makeoverPageVar = MakeoverPage.Body.varbitValue

    if (player.makeoverMageBodyTypeVar != bodyType) {
        player.makeoverMageBodyTypeVar = bodyType
    }
    val skin = appearance.currentSkinColour()
    if (player.makeoverMageSkinVar != skin) {
        player.makeoverMageSkinVar = skin
    }
    if (player.makeoverMagePronounVar != player.committedPronounVar) {
        player.makeoverMagePronounVar = player.committedPronounVar
    }

    ifOpenMainModal(MAKEOVER_MAGE_INTERFACE, colour = -1, transparency = -1)

    ifSetText(MAGE_CONFIRM_LABEL, "Confirm")
    ifSetText(MAGE_APPLY_LABEL, "Apply")
    ifSetEvents(MAGE_SKIN_COLOURS, 0..MakeoverPage.MAGE_SKIN_END, IfEvent.Op1)
    ifSetEvents(MAGE_BURGER_MENU, MakeoverPage.NAV_RANGE, IfEvent.Op1)

    startMakeoverBooth()
}

internal fun ProtectedAccess.navigateMakeover(sub: Int) {
    val session = MakeoverSessions.current(player) ?: return
    val page = MakeoverPage.fromNavSub(sub) ?: return
    changeMakeoverPage(session, page)
}

private fun ProtectedAccess.changeMakeoverPage(session: MakeoverSession, page: MakeoverPage) {
    session.navigating = true
    try {
        session.restore(player)
        ifCloseSub(session.page.interfaceName)
        showMakeoverPage(session, page)
    } finally {
        session.navigating = false
    }
}

internal fun ProtectedAccess.selectMakeoverStyle(sub: Int) {
    val session = MakeoverSessions.current(player) ?: return
    if (sub < 0 || sub % MakeoverPage.STYLE_SUB_STEP != 0) {
        return
    }
    val page = session.page
    val slot = page.identKitSlot ?: return
    val table = MakeoverStyles.tableFor(page) ?: return
    val index = sub / MakeoverPage.STYLE_SUB_STEP
    val kit = table.kitAt(index, player.appearance.bodyType) ?: return

    player.makeoverStyleVar = index
    player.appearance.setIdentKit(slot, kit)
    player.rebuildAppearance()
}

internal fun ProtectedAccess.selectMakeoverColour(sub: Int) {
    val session = MakeoverSessions.current(player) ?: return
    if (sub < 0 || sub % MakeoverPage.COLOUR_SUB_STEP != 0) {
        return
    }
    val page = session.page
    val slot = page.colourSlot ?: return
    val index = sub / MakeoverPage.COLOUR_SUB_STEP
    if (index >= page.colourCount) {
        return
    }
    player.makeoverColourVar = index
    player.appearance.setColour(slot, index)
    player.rebuildAppearance()
}

internal fun ProtectedAccess.confirmMakeover(close: Boolean) {
    val session = MakeoverSessions.current(player) ?: return
    val page = session.page
    val changed = session.isDirty(player)
    session.commit(player)
    if (changed) {
        page.confirmMessage?.let { player.mes(it) }
    }
    if (close) {
        closeMakeoverWindow(page)
        return
    }
    // Post-commit, so the rollback inside the page change is a no-op.
    page.selectAdvancesTo?.let { next -> changeMakeoverPage(session, next) }
}

internal fun ProtectedAccess.applyMakeoverMage(close: Boolean) {
    val session = MakeoverSessions.current(player) ?: return
    val appearance = player.appearance
    val stagedBodyType = player.makeoverMageBodyTypeVar
    val stagedPronoun = player.makeoverMagePronounVar
    val stagedSkin = player.makeoverMageSkinVar

    val changed =
        stagedBodyType != appearance.bodyType ||
            stagedPronoun != player.committedPronounVar ||
            stagedSkin != appearance.currentSkinColour()

    if (changed) {
        if (stagedBodyType != appearance.bodyType) {
            convertIdentKits(from = appearance.bodyType, to = stagedBodyType)
            appearance.bodyType = stagedBodyType
        }
        player.committedPronounVar = stagedPronoun
        appearance.pronoun = stagedPronoun
        appearance.setColour(SKIN_COLOUR_SLOT, stagedSkin)
        rebuildAppearance()
        // Apply is a commit: a later cancel on 516 must not undo it.
        session.commit(player)
        mes("Your changes have been applied!")
        soundSynth(MAGE_APPLY_SYNTH)
    } else {
        mes("You haven't changed anything.")
    }

    if (close) {
        ifCloseSub(MAKEOVER_MAGE_INTERFACE)
    }
}

internal fun ProtectedAccess.selectMakeoverBodyType(bodyType: Int) {
    MakeoverSessions.current(player) ?: return
    val pronoun =
        if (bodyType == Appearance.BODY_TYPE_B) Appearance.PRONOUN_SHE else Appearance.PRONOUN_HE
    if (player.makeoverMagePronounVar != pronoun) {
        player.makeoverMagePronounVar = pronoun
    }
    player.makeoverMageBodyTypeVar = bodyType
}

internal fun ProtectedAccess.selectMakeoverPronoun(pronoun: Int) {
    MakeoverSessions.current(player) ?: return
    player.makeoverMagePronounVar = pronoun
}

internal fun ProtectedAccess.selectMakeoverSkin(sub: Int) {
    MakeoverSessions.current(player) ?: return
    val colour = MAGE_SKIN_PALETTE.getOrNull(sub) ?: return
    player.makeoverMageSkinVar = colour
}

internal fun ProtectedAccess.closeMakeoverWindow(page: MakeoverPage) {
    ClientScripts.topLevelMainModalOpen(player, colour = -1, transparency = -1)
    ifCloseSub(page.interfaceName)
}

internal fun revertMakeoverOnClose(player: Player) {
    val session = MakeoverSessions.current(player) ?: return
    if (session.navigating) {
        return
    }
    session.restore(player)
    MakeoverSessions.discard(player)
}

private const val MAGE_APPLY_SYNTH = "synth.INTERFACE_SELECT"

private fun Appearance.currentSkinColour(): Int =
    coloursSnapshot().getOrNull(SKIN_COLOUR_SLOT)?.toInt()?.and(0xFF) ?: 0

// Map body-type styles by index; the jaw resets to index zero.
private fun ProtectedAccess.convertIdentKits(from: Int, to: Int) {
    val appearance = player.appearance
    val worn = appearance.identKitSnapshot()
    for (page in MakeoverPage.entries) {
        val slot = page.identKitSlot ?: continue
        val table = MakeoverStyles.tableFor(page) ?: continue
        val current = worn.getOrNull(slot)?.toInt() ?: continue
        val index = if (page == MakeoverPage.FacialHair) 0 else table.indexOfKit(current, from)
        val converted = table.kitAt(index, to) ?: continue
        appearance.setIdentKit(slot, converted)
    }
}

private fun ProtectedAccess.currentStyleIndex(page: MakeoverPage): Int {
    val slot = page.identKitSlot ?: return 0
    val table = MakeoverStyles.tableFor(page) ?: return 0
    val worn = player.appearance.identKitSnapshot().getOrNull(slot)?.toInt() ?: return 0
    return table.indexOfKit(worn, player.appearance.bodyType)
}
