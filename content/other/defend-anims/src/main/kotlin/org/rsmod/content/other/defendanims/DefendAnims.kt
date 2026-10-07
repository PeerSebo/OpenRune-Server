package org.rsmod.content.other.defendanims

import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos

object DefendAnims {
    const val UNARMED_BLOCK = "seq.human_unarmedblock"
    const val SHIELD_BLOCK = "seq.human_shield_defence"
    const val DEFENDER_BLOCK = "seq.warguild_parry_defend"

    private val shieldKeywords =
        listOf("shield", "defender", "ward", "book", "tome", "buckler", "deflector", "ket-xil")

    private val weaponCategoryBlocks: Map<WeaponCategory, String> =
        mapOf(
            WeaponCategory.Axe to "seq.human_axe_block",
            WeaponCategory.Pickaxe to "seq.human_axe_block",
            WeaponCategory.Claw to "seq.human_axe_block",
            WeaponCategory.Blunt to "seq.human_blunt_block",
            WeaponCategory.Spiked to "seq.human_blunt_block",
            WeaponCategory.Bludgeon to "seq.slayer_granite_maul_defend",
            WeaponCategory.SlashSword to "seq.human_sword_def",
            WeaponCategory.StabSword to "seq.human_sword_def",
            WeaponCategory.Salamander to "seq.human_sword_def",
            WeaponCategory.TwoHandedSword to "seq.human_dhsword_block",
            WeaponCategory.GodSword to "seq.dh_sword_update_defend",
            WeaponCategory.Polearm to "seq.human_spear_block",
            WeaponCategory.Spear to "seq.human_spear_block",
            WeaponCategory.Polestaff to "seq.human_staff_block",
            WeaponCategory.Staff to "seq.human_staff_block",
            WeaponCategory.BladedStaff to "seq.human_stafforb_block",
            WeaponCategory.PoweredStaff to "seq.human_stafforb_block",
            WeaponCategory.Scythe to "seq.human_scythe_block",
            WeaponCategory.Whip to "seq.slayer_abyssal_whip_defend",
            WeaponCategory.Banner to "seq.human_banner_block",
            WeaponCategory.Bulwark to "seq.human_dinhs_bulwark_block",
            WeaponCategory.Chinchompas to "seq.human_chinchompa_defend",
        )

    private val itemOverrides: Map<String, String> = emptyMap()

    val allAnimations: Set<String> =
        buildSet {
            add(SHIELD_BLOCK)
            add(DEFENDER_BLOCK)
            addAll(weaponCategoryBlocks.values)
            addAll(itemOverrides.values)
        }

    fun resolve(
        name: String,
        wearpos: Int,
        category: WeaponCategory,
        hasDefendAnim: Boolean,
    ): String? {
        if (hasDefendAnim) {
            return null
        }
        val lowercase = name.lowercase()
        itemOverrides[lowercase]?.let {
            return it
        }
        return when (wearpos) {
            Wearpos.LeftHand.slot -> resolveOffhand(lowercase)
            Wearpos.RightHand.slot -> weaponCategoryBlocks[category]
            else -> null
        }
    }

    private fun resolveOffhand(lowercaseName: String): String? {
        if (shieldKeywords.none { it in lowercaseName }) {
            return null
        }
        return if ("defender" in lowercaseName) DEFENDER_BLOCK else SHIELD_BLOCK
    }
}
