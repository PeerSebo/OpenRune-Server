package org.rsmod.content.skills.magic.utility

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.commons.magic.magicChargeUntil
import org.rsmod.api.combat.commons.magic.markOfDarknessUntil
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatMagicAttributeCollector
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.back
import org.rsmod.api.player.cheat.adminInfiniteRunes
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.content.skills.magic.utility.configs.UtilityMethod
import org.rsmod.content.skills.magic.utility.configs.UtilitySpellRules
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class UtilitySpellDispatchTest {
    @Test
    fun `each inventory spell consumes its native runes and transforms eligible items`() {
        for (rule in UtilitySpellRules.all.filter { it.batch > 0 && it.obelisk == null }) {
            val f = Fixture()
            val spell = checkNotNull(spells.getObjSpell(rule.spell))
            val recipe = UtilitySpellRules.conversions(rule.method, false).first()
            f.player.inv[0] = InvObj(recipe.input)
            f.player.inv[1] = InvObj("obj.coins", 10000)
            for ((index, rune) in spell.objReqs.withIndex()) {
                f.player.inv[index + 2] = InvObj(rune.obj, rune.count * 2)
            }
            f.player.adminInfiniteRunes = false
            f.cast(rule.method)
            assertEquals(recipe.output?.id, f.player.inv[0]?.id, rule.method.name)
            for (rune in spell.objReqs) assertEquals(rune.count, f.player.inv.count(rune.obj.internalName), rule.method.name)
            assertEquals((spell.castXp * 10).toInt(), f.player.statMap.getFineXP("stat.magic"), rule.method.name)
            assertEquals(10000 - recipe.coins, f.player.inv.count("obj.coins"), rule.method.name)
        }
    }

    @Test
    fun `spin flax works in a full inventory and repeated clicks do not double cast`() {
        val f = Fixture()
        for (slot in f.player.inv.indices) f.player.inv[slot] = InvObj("obj.flax")
        f.cast(UtilityMethod.SpinFlax)
        f.cast(UtilityMethod.SpinFlax)
        assertEquals(5, f.player.inv.count("obj.bow_string"))
        assertEquals(23, f.player.inv.count("obj.flax"))
        assertEquals(750, f.player.statMap.getFineXP("stat.magic"))
        assertEquals(750, f.player.statMap.getFineXP("stat.crafting"))
    }

    @Test
    fun `missing runes input level fees and wrong spellbook do not change inventory`() {
        for (failure in listOf("runes", "input", "level", "fees", "book")) {
            val f = Fixture()
            val method = if (failure == "fees") UtilityMethod.PlankMake else UtilityMethod.SpinFlax
            val rule = UtilitySpellRules.all.single { it.method == method }
            f.player.inv[0] = InvObj(if (failure == "fees") "obj.logs" else "obj.flax")
            val spell = checkNotNull(spells.getObjSpell(rule.spell))
            VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", spell.spellbook!!.varValue)
            when (failure) {
                "runes" -> f.player.adminInfiniteRunes = false
                "input" -> f.player.inv[0] = InvObj("obj.pot_empty")
                "level" -> f.player.statMap.setCurrentLevel("stat.magic", 1)
                "book" -> VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 0)
            }
            val before = f.player.inv.objs.toList()
            f.cast(method, setBook = false)
            assertEquals(before, f.player.inv.objs.toList(), failure)
            assertEquals(0, f.player.statMap.getFineXP("stat.magic"), failure)
            assertEquals(EntitySeq.NULL, f.player.pendingSequence, failure)
        }
    }

    @Test
    fun `automatic stringing stops when the current action is interrupted`() {
        val f = Fixture()
        for (slot in 0..2) f.player.inv[slot] = InvObj("obj.unstrung_gold_amulet")
        f.cast(UtilityMethod.StringJewellery)
        assertEquals(1, f.player.inv.count("obj.strung_gold_amulet"))
        f.tick(3)
        assertEquals(2, f.player.inv.count("obj.strung_gold_amulet"))
        f.player.clearPendingAction(f.events)
        f.tick(10)
        assertEquals(2, f.player.inv.count("obj.strung_gold_amulet"))
    }

    @Test
    fun `humidify waters seedlings and preserves the farming growth deadline`() {
        val f = Fixture()
        val seedling = UtilitySpellRules.conversions(UtilityMethod.Humidify, false).first { it.seedling }
        f.player.inv[0] = InvObj(seedling.input)
        f.player.inv[1] = InvObj("obj.watering_can_0")
        f.cast(UtilityMethod.Humidify)
        assertEquals(seedling.output!!.id, f.player.inv[0]!!.id)
        assertEquals(InvObj("obj.watering_can_8"), f.player.inv[1])
        assertTrue(f.player.vars["varp.farming_seedling_ready"] > 0)
    }

    @Test
    fun `cure me reduces venom to poison then cures without granting immunity`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varp.venom_strikes", 1)
        f.cast(UtilityMethod.CureMe)
        assertFalse(PlayerVenom.isEnvenomed(f.player))
        assertTrue(PlayerPoison.isPoisoned(f.player))
        f.cast(UtilityMethod.CureMe)
        assertTrue(PlayerPoison.isPoisoned(f.player))
        f.tick(1)
        f.cast(UtilityMethod.CureMe)
        assertFalse(PlayerPoison.isPoisoned(f.player))
    }

    @Test
    fun `vile vigour consumes only prayer needed for missing run energy`() {
        val f = Fixture()
        f.player.runEnergy = 9750
        f.cast(UtilityMethod.VileVigour)
        assertEquals(10000, f.player.runEnergy)
        assertEquals(96, f.player.stat("stat.prayer"))
    }

    @Test
    fun `buffs expire and logout clears authoritative state independently of display preferences`() {
        val f = Fixture()
        f.cast(UtilityMethod.ShadowVeil)
        assertEquals(1, f.player.vars["varbit.arceuus_shadow_veil_active"])
        f.cast(UtilityMethod.MarkOfDarkness)
        f.cast(UtilityMethod.Charge)
        assertEquals(397, f.player.markOfDarknessUntil)
        assertEquals(800, f.player.magicChargeUntil)
        f.tick(99)
        assertEquals(0, f.player.vars["varbit.arceuus_shadow_veil_active"])
        f.support.cleanup(f.player)
        assertEquals(0, f.player.magicChargeUntil)
        assertEquals(0, f.player.markOfDarknessUntil)
    }

    @Test
    fun `all orb spells reject wrong obelisks distant casts and moved origins`() {
        for (rule in UtilitySpellRules.all.filter { it.method == UtilityMethod.ChargeOrb }) {
            val spell = checkNotNull(spells.getObjSpell(rule.spell))
            for (scenario in listOf("success", "wrong obelisk", "distance", "moved", "same tick")) {
                val f = Fixture()
                f.player.coords = CoordGrid(3000, 3000, 0)
                f.player.inv[0] = InvObj("obj.stafforb")
                val id = if (scenario == "wrong obelisk") "loc.obelisk_earth".asRSCM()
                    else rule.obelisk!!.asRSCM()
                val loc = BoundLocInfo(CoordGrid(3001, 3000, 0),
                    LocEntity(id, LocShape.CentrepieceStraight.id, 0), 2, 1, 1, 0)
                val invalidObelisk = scenario == "wrong obelisk" && id != rule.obelisk!!.asRSCM()
                if (scenario == "distance") f.player.coords = CoordGrid(2999, 3000, 0)
                if (scenario == "same tick") f.player.actionDelay = 101
                val origin = if (scenario == "moved") CoordGrid(2999, 2999, 0) else f.player.coords
                VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 0)
                f.launcher.launch(f.player) {
                    with(f.orbs) { cast(spell, rule, loc, origin) }
                }
                val succeeds = scenario == "success" || (scenario == "wrong obelisk" && !invalidObelisk)
                assertEquals(if (succeeds) rule.orb!!.id else "obj.stafforb".asRSCM(), f.player.inv[0]!!.id,
                    "${rule.spell.internalName}: $scenario")
                assertEquals(if (succeeds) (spell.castXp * 10).toInt() else 0,
                    f.player.statMap.getFineXP("stat.magic"), scenario)
            }
        }
    }

    @Test
    fun `offerings accept a partial batch restore higher bones correctly and leave boosted prayer alone`() {
        for (points in listOf(95, 103)) {
            val f = Fixture()
            f.player.statMap.setCurrentLevel("stat.prayer", points.toByte())
            f.player.inv[0] = InvObj("obj.dragon_bones_superior")
            f.player.inv[1] = InvObj("obj.dragon_bones")
            f.cast(UtilityMethod.SinisterOffering)
            assertNull(f.player.inv[0])
            assertNull(f.player.inv[1])
            assertEquals(if (points == 95) 98 else 103, f.player.stat("stat.prayer"))
            assertEquals(6660, f.player.statMap.getFineXP("stat.prayer"))
        }
    }

    @Test
    fun `offerings have a shared cooldown without altering the combat delay`() {
        val f = Fixture()
        f.player.actionDelay = 105
        f.player.inv[0] = InvObj("obj.bones")
        f.player.inv[1] = InvObj("obj.infernal_ashes")
        f.cast(UtilityMethod.SinisterOffering)
        assertNull(f.player.inv[0])
        assertEquals(105, f.player.actionDelay)
        f.cast(UtilityMethod.DemonicOffering)
        assertEquals(InvObj("obj.infernal_ashes"), f.player.inv[1])
        f.tick(9)
        f.cast(UtilityMethod.DemonicOffering)
        assertNull(f.player.inv[1])
    }

    @Test
    fun `combat bonuses use live buff state and a matching god cape rather than disabled display settings`() {
        val f = Fixture()
        val collector = CombatMagicAttributeCollector()
        val spell = checkNotNull(ServerCacheManager.getItem("obj.60_saradomin_strike".asRSCM()))
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = maxExclusive - 1
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.5
        }
        fun attributes() = collector.spellCollect(f.player, spell, Spellbook.Standard, false, random)
        VarPlayerIntMapSetter.set(f.player, "varbit.buff_mark_of_darkness_disabled", 1)
        VarPlayerIntMapSetter.set(f.player, "varbit.buff_charge_spell_disabled", 1)
        assertFalse(CombatSpellAttributes.MarkOfDarkness in attributes())
        assertFalse(CombatSpellAttributes.ChargeSpell in attributes())
        f.player.magicChargeUntil = 200
        f.player.markOfDarknessUntil = 200
        f.player.back = InvObj("obj.zamorak_cape")
        assertFalse(CombatSpellAttributes.ChargeSpell in attributes())
        f.player.back = InvObj("obj.saradomin_cape")
        assertTrue(CombatSpellAttributes.ChargeSpell in attributes())
        assertTrue(CombatSpellAttributes.MarkOfDarkness in attributes())
        f.player.currentMapClock = 200
        assertFalse(CombatSpellAttributes.ChargeSpell in attributes())
        assertFalse(CombatSpellAttributes.MarkOfDarkness in attributes())
    }

    private class Fixture {
        val clock = MapClock(100)
        val player = Player().apply {
            client = RecordingClient()
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            for (stat in listOf("magic", "crafting", "cooking", "herblore", "prayer")) {
                statMap.setBaseLevel("stat.$stat", 99)
                statMap.setCurrentLevel("stat.$stat", 99)
            }
            inv = Inventory.create("inv.inv")
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            adminInfiniteRunes = true
        }
        val events = EventBus()
        private val contextFactory = unused<ProtectedAccessContextFactory>().also {
            it.javaClass.getDeclaredField("eventBus").apply { isAccessible = true }.set(it, events)
        }
        val launcher = ProtectedAccessLauncher(contextFactory)
        private val queues = PlayerQueueProcessor(events, launcher)
        private val inventory = InventoryUtilitySpellScript(events, launcher, spells, runes)
        val support = SupportSpellScript(spells, runes)
        val orbs = ChargeOrbScript(spells, runes)

        init {
            with(inventory) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            with(support) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun cast(method: UtilityMethod, setBook: Boolean = true) {
            val rule = UtilitySpellRules.all.single { it.method == method }
            val spell = checkNotNull(spells.getObjSpell(rule.spell))
            if (setBook) VarPlayerIntMapSetter.set(player, "varbit.spellbook", spell.spellbook!!.varValue)
            launcher.launch(player) {
                if (rule.batch > 0) with(inventory) { cast(spell, rule, null, false) }
                else with(support) { cast(spell, rule) }
            }
            queues.process(player)
        }

        fun tick(count: Int) = repeat(count) {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            queues.process(player)
            support.onPostTick(player)
        }
    }

    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private val restoreGlobals = mutableListOf<() -> Unit>()
        private lateinit var spells: MagicSpellRegistry
        private lateinit var runes: MagicRuneManager

        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restoreGlobals += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restoreGlobals += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
            spells = initialized(MagicSpellRegistry())
            runes = MagicRuneManager(
                initialized(FakeRuneRepository()), initialized(ComboRuneRepository()),
                initialized(CompactRuneRepository()), initialized(UnlimitedRuneRepository()),
                initialized(StaffSubstituteRepository()), initialized(RuneSubstituteRepository()), emptySet(),
            )
        }

        @JvmStatic @AfterAll
        fun restore() {
            restoreGlobals.asReversed().forEach { it() }
            restoreGlobals.clear()
        }

        private fun <T : Any> initialized(value: T): T = value.also {
            it.javaClass.declaredMethods.single { method -> method.name.startsWith("init") && method.parameterCount == 0 }
                .apply { isAccessible = true }.invoke(it)
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
