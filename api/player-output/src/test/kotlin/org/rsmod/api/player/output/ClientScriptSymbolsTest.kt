package org.rsmod.api.player.output

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class ClientScriptSymbolsTest {
    @Test fun `symbolic clientscript calls preserve argument types order and packet identity`() {
        val client = RecordingClient()
        val player = Player().apply { this.client = client }
        val args = listOf<Any>(123, "text", -1)
        player.runClientScript(2498, args)
        player.runClientScript("clientscript.[clientscript,account_info_update]", args)
        player.runClientScript("clientscript.[clientscript,account_info_update]", *args.toTypedArray())
        assertEquals(3, client.messages.size)
        assertEquals(client.messages[0], client.messages[1])
        assertEquals(client.messages[0], client.messages[2])
        assertThrows(IllegalArgumentException::class.java) { player.runClientScript("obj.coins") }
        assertEquals(3, client.messages.size)
    }

    @Test fun `migrated content symbols resolve to the original revision script IDs`() {
        val ids = mapOf(
            "meslayer_close" to 101, "ge_pricechecker_prices" to 785, "playermember" to 828,
            "script876" to 876, "deathkeep_init" to 972, "worldmap_transmitdata" to 1749,
            "skill_guide_v2_init" to 1902, "chatdefault_restoreinput" to 2158,
            "toplevel_chatbox_resetbackground" to 2379, "account_info_update" to 2498,
            "script3331" to 3331, "highlight_noclick" to 3406, "highlight_noclick_clear" to 3407,
            "highlight_start" to 3408, "highlight_screen" to 3409, "highlight_screen_hide_layer" to 3412,
            "highlight_textbox" to 3413, "summary_sidepanel_combat_level_transmit" to 3954,
            "script3970" to 3970, "settings_client_mode" to 3998, "chatdefault_stopinput" to 4020,
            "script5141" to 5141, "questjournal_overview_setup" to 6821,
        )
        for ((symbol, id) in ids) {
            assertEquals(id, "clientscript.[clientscript,$symbol]".asRSCM(RSCMType.CLIENTSCRIPT), symbol)
        }
        assertEquals(626, "clientscript.[proc,poll_results_addquestion]".asRSCM(RSCMType.CLIENTSCRIPT))
        assertEquals(9340, "clientscript.skill_guide_legacy_init".asRSCM(RSCMType.CLIENTSCRIPT))
    }

    @Test fun `shared output symbols resolve to the original revision script IDs`() {
        val ids = mapOf(
            "script7192" to 7192, "notification_display_init" to 3343,
            "settings_interface_scaling" to 2358, "buff_bar_layout_redraw" to 5937,
            "playermember" to 828, "cc_deleteall" to 2249, "settings_set_draw_distance" to 6021,
            "highlighting_off" to 5485, "highlighting_on" to 5487, "cam_forceangle" to 143,
            "menu" to 217, "toplevel_sidebutton_switch" to 915, "chatbox_multi_init" to 58,
            "meslayer_close" to 101, "meslayer_mode7" to 108, "meslayer_mode9" to 110,
            "meslayer_mode14" to 750, "chatdefault_restoreinput" to 2158,
            "toplevel_mainmodal_open" to 2524, "toplevel_mainmodal_background" to 917,
            "toplevel_chatbox_resetbackground" to 2379, "if_settextalign" to 600,
            "objbox_setbuttons" to 2868, "interface_inv_init" to 149, "shop_main_init" to 1074,
            "examine_item" to 6003, "add_overlaytimer_loc" to 5474, "confirmdestroy_init" to 814,
            "pvp_icons_comlevelrange" to 5224, "stat_group_tooltip" to 7065,
            "script1495" to 1495, "script4212" to 4212, "hp_hud_open" to 2376,
            "fade_overlay" to 948, "script1508" to 1508,
        )
        for ((symbol, id) in ids) {
            assertEquals(id, "clientscript.[clientscript,$symbol]".asRSCM(RSCMType.CLIENTSCRIPT), symbol)
        }
    }

    @Test fun `shared wrappers preserve mixed arguments defaults and boolean encoding`() {
        val client = RecordingClient()
        val player = Player().apply { this.client = client }

        ClientScripts.notificationDisplay(player, "Title", "Reward")
        ClientScripts.lootTrackerAddLoot(player, 12, 34, 56, 78)
        ClientScripts.buffBarLayoutRedraw(player)
        ClientScripts.menu(player, "Choose", "First|Second", hotkeys = true)
        ClientScripts.playerMember(player, member = false)
        ClientScripts.mesLayerMode14(player, "Find item", stockMarketRestriction = false, showLastSearched = true)
        ClientScripts.topLevelMainModalOpen(player)
        ClientScripts.confirmDestroyInit(player, "Destroy?", "Cannot reclaim", 4151, 2)
        ClientScripts.examineItem(player, 4151, 2, "Whip", true, 12345, false, 120, 80)

        assertEquals(
            listOf(
                RunClientScript(3343, listOf("Title", "Reward", 0xff981f)),
                RunClientScript(7192, listOf(12, 34, 56, 78)),
                RunClientScript(5937, emptyList()),
                RunClientScript(217, listOf("Choose", "First|Second", 1)),
                RunClientScript(828, listOf(0)),
                RunClientScript(750, listOf("Find item", 0, -1, 1)),
                RunClientScript(2524, listOf(-1, -1)),
                RunClientScript(814, listOf(4151, 2, "Destroy?", "Cannot reclaim")),
                RunClientScript(6003, listOf(4151, 2, "Whip", 1, 12345, 0, 120, 80)),
            ),
            client.messages,
        )
    }

    @Test fun `content cleanup symbols preserve cache identity and outgoing packets`() {
        val symbols = mapOf(
            "clientscript.[clientscript,skillmulti_setup]" to 2046,
            "clientscript.[clientscript,barrows_nodnod]" to 894,
            "clientscript.[clientscript,wilderness_lootingbag_setup]" to 495,
            "clientscript.[clientscript,xpdrops_fake]" to 2091,
            "clientscript.[clientscript,xptracker_update_goal]" to 5538,
            "clientscript.[clientscript,colour_pallet]" to 4185,
            "clientscript.[clientscript,notification_end]" to 3348,
            "synth.ANVIL02" to 3771,
            "synth.BONES_DOWN" to 2738,
            "synth.QUICKSMASH" to 2715,
            "synth.GODWARS_GODSWORD_SPECIAL_ATTACK" to 3869,
            "synth.PUNCTURE" to 2537,
            "synth.SEVER" to 2540,
            "synth.ENERGYDRAIN" to 2713,
            "synth.EAT" to 2393,
            "synth.LIQUID" to 2401,
            "synth.MINE_ORE" to 3600,
            "synth.ides_cowbell_teleport_arrive" to 11286,
            "synth.ides_brutus_spawn" to 11212,
            "synth.ides_brutus_snort" to 11213,
            "synth.ides_brutus_growl" to 11210,
            "spotanim.curse_travel" to 109,
            "param.collection_subcat_enum" to 690,
            "param.settings_category_settings_list" to 745,
            "param.collection_category_sublist" to 683,
            "synth.UNARMED_KICK" to 2565,
            "synth.FIRESTRIKE_CAST_AND_FIRE" to 160,
            "synth.FIRESTRIKE_HIT" to 161,
            "synth.CURSE_CAST_AND_FIRE" to 127,
            "synth.CURSE_HIT" to 126,
            "synth.WEAKEN_CAST_AND_FIRE" to 3011,
            "synth.WEAKEN_HIT" to 3010,
            "synth.SPELLFAIL" to 227,
            "loc.trawler_hull_00" to 37353,
            "obj.swamppaste" to 1941,
            "clientscript.[clientscript,buff_bar_start]" to 5931,
            "clientscript.[clientscript,script2085]" to 2085,
        )
        val client = RecordingClient()
        val player = Player().apply { this.client = client }
        for ((symbol, id) in symbols) {
            assertEquals(id, symbol.asRSCM(), symbol)
            client.messages.clear()
            when {
                symbol.startsWith("clientscript.") -> {
                    player.runClientScript(id, 17, "text", -1)
                    player.runClientScript(symbol, 17, "text", -1)
                }
                symbol.startsWith("synth.") -> {
                    player.soundSynth(id, loops = 3, delay = 20)
                    player.soundSynth(symbol, loops = 3, delay = 20)
                }
                else -> continue
            }
            assertEquals(2, client.messages.size, symbol)
            assertEquals(client.messages[0], client.messages[1], symbol)
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
