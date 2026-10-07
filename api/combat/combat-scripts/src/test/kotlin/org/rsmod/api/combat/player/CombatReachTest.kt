package org.rsmod.api.combat.player

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.MagicSpellType
import org.rsmod.api.combat.commons.styles.AttackStyle
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CombatReachTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `stale autocast selection cannot give an axe or unarmed attack spell reach`() {
        val access = access()
        access.autocastEnabled = true
        VarPlayerIntMapSetter.set(access.player, "varbit.autocast_spell", 1)
        assertEquals(1, access.attackRange(null, null))
        equip(access, "obj.bronze_axe")
        assertEquals(1, access.attackRange(AttackStyle.AccurateMelee, null))
        assertTrue(access.autocastEnabled)
    }

    @Test fun `halberd and ranged reach survive a failed autocast`() {
        val access = access()
        access.autocastEnabled = true
        equip(access, "obj.bronze_halberd")
        assertEquals(2, access.attackRange(AttackStyle.AccurateMelee, null))
        equip(access, "obj.shortbow")
        assertEquals(7, access.attackRange(AttackStyle.AccurateRanged, null))
        assertEquals(9, access.attackRange(AttackStyle.LongrangeRanged, null))
    }

    @Test fun `a resolved spell still uses spell range`() {
        val access = access()
        val spellObj = checkNotNull(ServerCacheManager.getItem("obj.01_wind_strike".asRSCM()))
        val spell = MagicSpell(
            obj = spellObj,
            name = "Wind Strike", component = spellObj.param(BaseParams.spell_button), spellbook = null,
            type = MagicSpellType.Combat, maxHit = 2, levelReq = 1, castXp = 0.0,
            objReqs = emptyList(),
        )
        assertEquals(10, access.attackRange(null, spell))
    }

    private fun equip(access: ProtectedAccess, name: String) {
        access.player.worn[Wearpos.RightHand.slot] = InvObj(name)
    }

    private fun access(): ProtectedAccess {
        val player = Player().apply {
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        return ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { error("unused") }, getEventBus = { error("unused") },
            getNpcList = { error("unused") }, getPlayerList = { error("unused") },
            getCollision = { error("unused") }, getAreaChecker = { error("unused") },
            getAlignment = { error("unused") }, getLocInteractions = { error("unused") },
            getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") },
            getMusicPlayer = { error("unused") }, getMarketPrices = { error("unused") },
            getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
            getWorldRepo = { error("unused") }, getHitModifier = { error("unused") },
        ))
    }
}
