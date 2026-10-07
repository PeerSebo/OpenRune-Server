package org.rsmod.content.other.xprates

import com.github.michaelbull.logging.InlineLogger
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import kotlin.math.roundToLong
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.config.updater.RealmConfigUpdater
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class XpRatesScript
@Inject
constructor(
    private val realm: Realm,
    private val realmUpdater: RealmConfigUpdater,
    private val playerList: PlayerList,
    private val levelReset: XpLevelReset,
    private val protectedAccess: ProtectedAccessLauncher,
    private val serverConfig: ServerConfig,
    private val loader: XpRateConfigLoader,
) : PluginScript() {
    private val logger = InlineLogger()

    private var config: XpRateConfig = XpRateConfig()

    override fun ScriptContext.startup() {
        config = loader.loadOrCreate()
        logger.info {
            if (config.enabled) {
                "XP overrides enabled: rate=${config.rate}x global=${config.globalRate}x " +
                    "enforceOnLogin=${config.enforceOnLogin} (xp-rates.yml)"
            } else {
                "XP rate: base=${serverConfig.gameplay.xpRate}x (game.yml); " +
                    "xp-rates.yml overrides disabled; event multiplier supplied by realm at login."
            }
        }

        onPlayerLogin { player.applyRates() }

        onCommand("xprates") {
            desc = "Show your current experience rate"
            cheat { player.reportRates() }
        }

        onCommand("setxprate") {
            desc = "Set a player's personal experience rate"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::setxprate <rate> [player]"
            cheat(::setXpRate)
        }

        onCommand("globalxprate") {
            desc = "Set the server-wide experience multiplier"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::globalxprate <rate>"
            cheat(::setGlobalXpRate)
        }

        onCommand("reloadxprates") {
            desc = "Reload xp-rates.yml and re-apply it to everyone online"
            requiredRights = Rights.ADMINISTRATOR
            cheat(::reloadRates)
        }

        onCommand("resetxp") {
            desc = "Reset a player's skills to fresh-account levels"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::resetxp [player]"
            cheat(::resetXp)
        }

        onCommand("resetxpall") {
            desc = "Reset every online player's skills to fresh-account levels"
            requiredRights = Rights.ADMINISTRATOR
            cheat(::resetXpAll)
        }
    }

    private fun Player.applyRates() {
        XpRateState.migrate(this)
        XpRateState.personalRateHundredths(this)?.let { xpRate = it / 100.0 }
        if (!config.enabled) {
            return
        }

        globalXpRate = config.globalRate

        if (config.enforceOnLogin && xpRate != config.rate) {
            val previous = xpRate
            xpRate = config.rate
            logger.info {
                "Corrected stored xp rate for '$username': " +
                    "${format(previous)}x -> ${format(config.rate)}x"
            }
        }

        val effective = effectiveRate()
        if (effective != XpRateConfig.AUTHENTIC_RATE) {
            mes("Experience rate: <col=800000>${format(effective)}x</col>.")
        }

        resetLevelsOnce()
    }

    private fun Player.resetLevelsOnce() {
        if (!config.resetExistingLevels || XpRateState.levelsResetStamp(this) != null) {
            return
        }
        XpRateState.markLevelsReset(this)

        val changed = levelReset.reset(this)
        if (changed > 0) {
            mes("Your skills have been reset to suit the server's experience rates.")
            logger.info { "Reset $changed skills for '$username' (one-time xp rate migration)." }
        }
    }

    private fun Player.reportRates() {
        mes("Your experience rate: <col=800000>${format(effectiveRate())}x</col>.")
        if (globalXpRate != XpRateConfig.AUTHENTIC_RATE) {
            mes(
                "(personal <col=800000>${format(xpRate)}x</col> " +
                    "x server-wide <col=800000>${format(globalXpRate)}x</col>)"
            )
        }
    }

    private fun setXpRate(cheat: Cheat) =
        with(cheat) {
            val rate = XpRateConfig.clampRate(args[0].toDouble())
            val targetName = args.getOrNull(1)
            val target =
                if (targetName == null) {
                    player
                } else {
                    findOnline(targetName)
                        ?: run {
                            player.mes("No online player found named '$targetName'.")
                            return
                        }
                }

            target.xpRate = rate
            XpRateState.setPersonalRateHundredths(target, (rate * 100).roundToLong().toInt())
            target.mes("Your experience rate is now <col=800000>${format(rate)}x</col>.")
            if (target !== player) {
                player.mes("Set ${target.displayName}'s experience rate to ${format(rate)}x.")
            }
            if (config.enabled && config.enforceOnLogin) {
                player.mes(
                    "Note: `enforce-on-login` is on, so this resets to " +
                        "${format(config.rate)}x on their next login."
                )
            }
        }

    private fun setGlobalXpRate(cheat: Cheat) =
        with(cheat) {
            val rate = XpRateConfig.clampRate(args[0].toDouble())
            config = config.copy(globalRate = rate)
            realmUpdater.setGlobalXpRate(rate)
            for (online in playerList) {
                online.globalXpRate = rate
                online.mes("Server-wide experience rate is now <col=800000>${format(rate)}x</col>.")
            }
            player.mes("Edit xp-rates.yml to keep this across a reboot.")
            logger.info { "'${player.username}' set the server-wide xp rate to ${format(rate)}x." }
        }

    private fun reloadRates(cheat: Cheat) =
        with(cheat) {
            config = loader.loadOrCreate()
            for (online in playerList) {
                online.globalXpRate = if (config.enabled) config.globalRate else realm.config.globalXpRate
                if (config.enabled && config.enforceOnLogin) {
                    online.xpRate = config.rate
                } else {
                    val personalRate = XpRateState.personalRateHundredths(online)?.div(100.0)
                    online.xpRate = personalRate ?: serverConfig.gameplay.xpRate
                }
            }
            if (config.enabled) {
                player.mes(
                    "Reloaded xp-rates.yml: rate <col=800000>${format(config.rate)}x</col>, " +
                        "server-wide <col=800000>${format(config.globalRate)}x</col>."
                )
            } else {
                player.mes("XP overrides disabled; restored game.yml and realm rates.")
                player.reportRates()
            }
        }

    private fun resetXp(cheat: Cheat) =
        with(cheat) {
            val targetName = args.getOrNull(0)
            val target =
                if (targetName == null) {
                    player
                } else {
                    findOnline(targetName)
                        ?: run {
                            player.mes("No online player found named '$targetName'.")
                            return
                        }
                }
            val label = if (target === player) "your own character" else target.displayName

            player.confirmThen("Reset every skill on $label?") {
                val changed = applyReset(target)
                if (target === player) {
                    player.mes("Reset $changed ${plural(changed, "skill")}.")
                } else {
                    player.mes("Reset $changed ${plural(changed, "skill")} on ${target.displayName}.")
                    target.mes("An administrator has reset your skills.")
                }
                logger.info {
                    "'${player.username}' reset $changed skills on '${target.username}'."
                }
            }
        }

    private fun resetXpAll(cheat: Cheat) =
        with(cheat) {
            val online = playerList.count()
            player.confirmThen("Reset every skill on all $online online ${plural(online, "player")}?") {
                // Re-read the list: players can log in or out while the dialogue is open.
                var reset = 0
                for (target in playerList.toList()) {
                    applyReset(target)
                    if (target !== player) {
                        target.mes("An administrator has reset your skills.")
                    }
                    reset++
                }
                player.mes("Reset the skills of $reset ${plural(reset, "player")}.")
                logger.warn { "'${player.username}' reset the skills of $reset online players." }
            }
        }

    private fun applyReset(target: Player): Int {
        val changed = levelReset.reset(target)
        XpRateState.markLevelsReset(target)
        return changed
    }

    private fun Player.confirmThen(question: String, action: () -> Unit) {
        protectedAccess.launch(this, busyText = BUSY_TEXT) {
            if (choice2("Yes", true, "No", false, title = question)) {
                action()
            }
        }
    }

    private fun Player.effectiveRate(): Double = xpRate * globalXpRate

    private fun plural(count: Int, noun: String): String = if (count == 1) noun else "${noun}s"

    private fun findOnline(name: String): Player? =
        playerList.firstOrNull { it.displayName.equals(name.replace('_', ' '), ignoreCase = true) }

    private fun format(rate: Double): String {
        val rounded = (rate * 100).roundToLong() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    private companion object {
        private const val BUSY_TEXT = "Please finish what you're doing first."
    }
}
