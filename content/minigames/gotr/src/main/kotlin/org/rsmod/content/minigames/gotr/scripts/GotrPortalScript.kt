package org.rsmod.content.minigames.gotr.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseAgilityLvl
import org.rsmod.api.player.stat.runecraftingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.minigames.gotr.GotrGame
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GotrPortalScript
@Inject
constructor(
    private val game: GotrGame,
    private val random: GameRandom,
    private val bankScript: BankInvScript,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val altarEntrances =
            RunecraftingAltarsRow.all().associate { it.altarObject.internalName to it.entrance }

        for ((runeKey, portalLoc) in PORTAL_LOCS) {
            val altarLoc = ALTAR_LOC_FOR_RUNE[runeKey] ?: continue
            val dest = ALTAR_LANDING[runeKey] ?: altarEntrances[altarLoc]
            onOpLoc1(portalLoc) { enter(runeKey, dest) }
            onOpLoc2(portalLoc) { toggleTalisman(runeKey) }
            val talisman = "obj.gotr_portal_talisman_$runeKey"
            onOpHeld4(talisman) {
                mes("A talisman bound to a runic altar. Presenting this to the respective portal guardian will return you there.")
            }
            onOpHeld5(talisman) {
                val count = inv[it.slot]?.count ?: return@onOpHeld5
                if (invDel(inv, talisman, count, slot = it.slot).success) {
                    mes("Your portal talisman is destroyed as you drop it.")
                }
            }
        }

        onOpLoc1(AGILITY_SHORTCUT_TOP) { climb(AGILITY_SHORTCUT_EAST_LANDING) }
        onOpLoc1(AGILITY_SHORTCUT_BOTTOM) { climb(AGILITY_SHORTCUT_WEST_LANDING) }

        onOpLoc1(AGILITY_PORTAL_TOP) { enterHugeRemainsPortal() }
        onOpLoc1(AGILITY_PORTAL_BOTTOM_CHILD) { leaveHugeRemainsPortal() }

        onOpLoc1(DEPOSIT_CHEST) { depositRunes() }
        onOpLoc2(DEPOSIT_CHEST) { depositItems() }
    }

    private fun ProtectedAccess.depositRunes() {
        if (IronmanRestrictions.blockUimBank(player)) return
        val deposited = depositMatching { name -> name in ALL_RUNE_NAMES }
        if (deposited) {
            anim(DEPOSIT_ANIM)
            mes("You deposit all of your runes into the pool.")
        } else {
            mes("You have no runes to deposit into the pool.")
        }
    }

    private fun ProtectedAccess.depositItems() {
        if (IronmanRestrictions.blockUimBank(player)) return
        ifOpenMainModal("interface.bank_depositbox")
    }

    private fun ProtectedAccess.depositMatching(predicate: (String) -> Boolean): Boolean {
        var any = false
        for (slot in inv.indices) {
            val obj = inv[slot] ?: continue
            val name = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
            if (!predicate(name)) continue
            if (with(bankScript) { invDeposit(slot, obj.count, inv) }) any = true
        }
        return any
    }

    private suspend fun ProtectedAccess.enter(runeKey: String, entrance: CoordGrid?) {
        if (!game.isMember(player)) {
            mes("You need to be inside the Temple of the Eye to use this.")
            return
        }
        val naturallyOpen = game.isPortalOpen(runeKey)
        val talisman = "obj.gotr_portal_talisman_$runeKey"
        if (!game.portalsAvailable() || (!naturallyOpen && !inv.contains(talisman))) {
            mes("This rift is not currently active.")
            return
        }
        val altarName = RUNE_DISPLAY_NAME[runeKey] ?: runeKey
        val levelReq = RIFT_RC_LEVEL[runeKey] ?: 1
        if (player.runecraftingLvl < levelReq) {
            mesbox("You need a Runecraft level of $levelReq to enter the $altarName rift.")
            return
        }
        val dest = entrance ?: return
        val talismanSlot = if (naturallyOpen) -1 else inv.indexOfFirst { it?.id == talisman.asRSCM(RSCMType.OBJ) }
        val consumedStack = if (talismanSlot >= 0) inv[talismanSlot] else null
        if (!naturallyOpen && (consumedStack == null || invDel(inv, talisman, 1, slot = talismanSlot).failure)) return
        telejump(dest)
        if (player.coords != dest) {
            // No suspension occurs before teleport refusal; refund the exact key stack.
            if (consumedStack != null) inv[talismanSlot] = consumedStack
            return
        }
        game.visitAltar(player, dest)
        portalEffect()
        spam("You step through the rift and find yourself at the $altarName Altar.")
    }

    private fun ProtectedAccess.toggleTalisman(runeKey: String) {
        val toggleVarbit = TALISMAN_TOGGLE_VARBIT[runeKey] ?: return
        val current = vars[toggleVarbit]
        VarPlayerIntMapSetter.set(player, toggleVarbit, if (current == 0) 1 else 0)
        mes(
            if (current == 0) {
                "You will no longer receive portal talismans from this altar."
            } else {
                "You will receive portal talismans from this altar."
            },
        )
    }

    private fun ProtectedAccess.climb(dest: CoordGrid) {
        if (player.baseAgilityLvl < SHORTCUT_AGILITY_REQ) {
            mes("You need an Agility level of $SHORTCUT_AGILITY_REQ to use this shortcut.")
            return
        }
        telejump(dest)
    }

    private suspend fun ProtectedAccess.enterHugeRemainsPortal() {
        if (!game.isMember(player) || !game.isHugeRemainsPortalOpen()) {
            return
        }
        delay(PORTAL_TELEPORT_DELAY)
        if (!game.isMember(player)) return
        VarPlayerIntMapSetter.set(player, GotrGame.VARBIT_PORTAL_BOTTOM_VISIBLE, 1)
        telejump(HUGE_REMAINS_LANDING)
        if (player.coords == HUGE_REMAINS_LANDING) portalEffect()
        spam(PORTAL_STEP_THROUGH_MSG)
    }

    // Bind the rendered return leaf; the plateau varbit controls it.
    // Return landings scatter across the arena, independently of the entry portal.
    private suspend fun ProtectedAccess.leaveHugeRemainsPortal() {
        if (!game.isMember(player) || vars[GotrGame.VARBIT_PORTAL_BOTTOM_VISIBLE] == 0) return
        delay(PORTAL_TELEPORT_DELAY)
        if (!game.isMember(player)) return
        VarPlayerIntMapSetter.set(player, GotrGame.VARBIT_PORTAL_BOTTOM_VISIBLE, 0)
        telejump(random.pick(RETURN_LANDINGS))
        portalEffect()
        spam(PORTAL_STEP_THROUGH_MSG)
    }

    internal fun ProtectedAccess.portalEffect() {
        if (vars["varbit.gotr_colourful_fade_disabled"] != 0) return
        fadeOverlay(0x744499, 64, 0x744499, 255, 60)
        closeFadeOverlay(2)
    }

    private companion object {
        val PORTAL_LOCS =
            mapOf(
                "air" to "loc.gotr_portal_air",
                "water" to "loc.gotr_portal_water",
                "earth" to "loc.gotr_portal_earth",
                "fire" to "loc.gotr_portal_fire",
                "mind" to "loc.gotr_portal_mind",
                "chaos" to "loc.gotr_portal_chaos",
                "death" to "loc.gotr_portal_death",
                "blood" to "loc.gotr_portal_blood",
                "body" to "loc.gotr_portal_body",
                "cosmic" to "loc.gotr_portal_cosmic",
                "nature" to "loc.gotr_portal_nature",
                "law" to "loc.gotr_portal_law",
            )

        val ALTAR_LOC_FOR_RUNE =
            mapOf(
                "air" to "loc.air_altar",
                "water" to "loc.water_altar",
                "earth" to "loc.earth_altar",
                "fire" to "loc.fire_altar",
                "mind" to "loc.mind_altar",
                "chaos" to "loc.chaos_altar",
                "death" to "loc.death_altar",
                "blood" to "loc.blood_altar",
                "body" to "loc.body_altar",
                "cosmic" to "loc.cosmic_altar",
                "nature" to "loc.nature_altar",
                "law" to "loc.law_altar",
            )

        val RUNE_DISPLAY_NAME =
            mapOf(
                "air" to "Air",
                "water" to "Water",
                "earth" to "Earth",
                "fire" to "Fire",
                "mind" to "Mind",
                "chaos" to "Chaos",
                "death" to "Death",
                "blood" to "Blood",
                "body" to "Body",
                "cosmic" to "Cosmic",
                "nature" to "Nature",
                "law" to "Law",
            )

        val RIFT_RC_LEVEL =
            mapOf(
                "air" to 1,
                "mind" to 2,
                "water" to 5,
                "earth" to 9,
                "fire" to 14,
                "body" to 20,
                "cosmic" to 27,
                "chaos" to 35,
                "nature" to 44,
                "law" to 54,
                "death" to 65,
                "blood" to 77,
            )

        val ALTAR_LANDING =
            mapOf(
                "air" to CoordGrid(2841, 4829, 0),
                "water" to CoordGrid(2726, 4832, 0),
                "earth" to CoordGrid(2656, 4829, 0),
                "fire" to CoordGrid(2574, 4849, 0),
                "mind" to CoordGrid(2793, 4828, 0),
                "chaos" to CoordGrid(2281, 4837, 0),
                "body" to CoordGrid(2521, 4834, 0),
                "nature" to CoordGrid(2400, 4835, 0),
                "law" to CoordGrid(2464, 4818, 0),
            )

        val TALISMAN_TOGGLE_VARBIT =
            mapOf(
                "air" to "varbit.gotr_talisman_toggle_air",
                "water" to "varbit.gotr_talisman_toggle_water",
                "earth" to "varbit.gotr_talisman_toggle_earth",
                "fire" to "varbit.gotr_talisman_toggle_fire",
                "mind" to "varbit.gotr_talisman_toggle_mind",
                "chaos" to "varbit.gotr_talisman_toggle_chaos",
                "death" to "varbit.gotr_talisman_toggle_death",
                "blood" to "varbit.gotr_talisman_toggle_blood",
                "body" to "varbit.gotr_talisman_toggle_body",
                "cosmic" to "varbit.gotr_talisman_toggle_cosmic",
                "nature" to "varbit.gotr_talisman_toggle_nature",
                "law" to "varbit.gotr_talisman_toggle_law",
            )

        const val AGILITY_SHORTCUT_TOP = "loc.gotr_agility_shortcut_top"
        const val AGILITY_SHORTCUT_BOTTOM = "loc.gotr_agility_shortcut_bottom"
        const val SHORTCUT_AGILITY_REQ = 56

        val AGILITY_SHORTCUT_EAST_LANDING = CoordGrid(3637, 9503, 0)
        val AGILITY_SHORTCUT_WEST_LANDING = CoordGrid(3633, 9503, 0)

        const val AGILITY_PORTAL_TOP = "loc.gotr_agility_portal_top"
        const val AGILITY_PORTAL_BOTTOM_CHILD = "loc.gotr_agility_portal_bottom_child"
        const val DEPOSIT_CHEST = "loc.gotr_depositchest"
        const val DEPOSIT_ANIM = "seq.human_leverdown_walkmerge"

        const val PORTAL_TELEPORT_DELAY = 3

        val HUGE_REMAINS_LANDING = CoordGrid(3592, 9503, 0)

        const val PORTAL_STEP_THROUGH_MSG =
            "You step through the portal and find yourself in another part of the temple."

        val RETURN_LANDINGS =
            listOf(
                CoordGrid(3610, 9499, 0),
                CoordGrid(3610, 9495, 0),
                CoordGrid(3610, 9503, 0),
                CoordGrid(3613, 9497, 0),
                CoordGrid(3611, 9504, 0),
                CoordGrid(3616, 9496, 0),
                CoordGrid(3611, 9507, 0),
                CoordGrid(3614, 9498, 0),
                CoordGrid(3608, 9502, 0),
                CoordGrid(3618, 9499, 0),
                CoordGrid(3617, 9506, 0),
                CoordGrid(3618, 9498, 0),
                CoordGrid(3608, 9505, 0),
                CoordGrid(3616, 9497, 0),
            )

        val ALL_RUNE_NAMES =
            setOf(
                "obj.airrune",
                "obj.waterrune",
                "obj.earthrune",
                "obj.firerune",
                "obj.mindrune",
                "obj.bodyrune",
                "obj.cosmicrune",
                "obj.chaosrune",
                "obj.naturerune",
                "obj.lawrune",
                "obj.deathrune",
                "obj.bloodrune",
                "obj.soulrune",
                "obj.wrathrune",
                "obj.astralrune",
                "obj.aetherrune",
                "obj.dustrune",
                "obj.mudrune",
                "obj.mistrune",
                "obj.smokerune",
                "obj.steamrune",
                "obj.lavarune",
            )
    }
}
