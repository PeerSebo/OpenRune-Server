package org.rsmod.server.app

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.TypeLiteral
import com.google.inject.util.Modules
import net.rsprot.protocol.api.NetworkService
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.net.central.CentralWorldLinkSettings
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.server.config.ServerConfigLoader
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule
import org.rsmod.server.shared.PluginConstants
import org.rsmod.server.shared.loader.PluginModuleLoader

fun main() {
    val config = ServerConfig(
        name = "Plugin registration validation",
        gamePort = 0,
        revision = 241,
        environment = "LIVE",
        world = 255,
    )
    check(CentralWorldLinkSettings.resolve(config) == null) {
        "Central environment configuration must be cleared; use :server:app:validatePluginRegistration"
    }
    val modules = PluginModuleLoader.load(PluginModule::class.java, PluginConstants.searchPackages)
    val injector = Guice.createInjector(
        Modules.override(GameServerModule, *modules.toTypedArray()).with(ValidationGuards(config)),
    )
    println("Validating all plugin scripts except $EXCLUDED_NETWORK_SCRIPT")
    GameServer(skipTypeVerificationOverride = false, excludedPluginScripts = setOf(EXCLUDED_NETWORK_SCRIPT)).prepareGame(injector)
    System.getProperty("openrune.npcAudit")?.let { writeNpcInteractionAudit(injector, it) }
    println(
        "PLUGIN REGISTRATION VALIDATION PASSED: cache, map, plugin injection, event registration " +
            "and initial map spawns completed. Services, network script, lifecycle startup " +
            "callbacks, game cycles and account loading were not run.",
    )
}

private const val EXCLUDED_NETWORK_SCRIPT = "org.rsmod.api.net.rsprot.NetworkScript"

private class ValidationGuards(private val config: ServerConfig) : AbstractModule() {
    override fun configure() {
        bind(ServerConfig::class.java).toInstance(config)
        bind(ServerConfigLoader::class.java).toProvider {
            error("Server configuration file access is forbidden during plugin validation")
        }
        bind(DatabaseConfig::class.java).toProvider {
            error("Database access is forbidden during plugin validation")
        }
        bind(object : TypeLiteral<NetworkService<Player>>() {}).toProvider {
            error("Network service construction is forbidden during plugin validation")
        }
    }
}
