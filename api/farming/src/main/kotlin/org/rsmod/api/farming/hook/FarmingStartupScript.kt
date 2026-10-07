package org.rsmod.api.farming.hook

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.farming.FarmingBinRegistry
import org.rsmod.api.farming.FarmingCropRegistry
import org.rsmod.api.farming.FarmingPreResolveHook
import org.rsmod.api.farming.FarmingWorldData
import org.rsmod.api.script.onGameStartup
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class FarmingStartupScript
@Inject
constructor(
    private val crops: FarmingCropRegistry,
    private val bins: FarmingBinRegistry,
    private val preResolveHooks: Set<@JvmSuppressWildcards FarmingPreResolveHook>,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onGameStartup {
            FarmingWorldData.validate()
            crops.validate()
            bins.validate()
            logBoundHooks()
        }
    }

    private fun logBoundHooks() {
        if (preResolveHooks.isEmpty()) {
            logger.debug { "Farming: no pre-resolve hooks bound." }
            return
        }
        val names = preResolveHooks.joinToString { it::class.simpleName ?: "anonymous" }
        logger.debug { "Farming: ${preResolveHooks.size} pre-resolve hook(s) bound: $names." }
    }
}
