package org.rsmod.content.skills.sailing.scripts

import jakarta.inject.Inject
import org.rsmod.content.skills.sailing.BoatWorldEntityService
import org.rsmod.content.skills.sailing.CrewService
import org.rsmod.content.skills.sailing.FacilityService
import org.rsmod.content.skills.sailing.PortTaskManager
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.table.ShipbuildingXp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class SailingBootScript
@Inject
constructor(
    private val crew: CrewService,
    private val facilities: FacilityService,
    private val boats: BoatWorldEntityService,
    private val seaMap: org.rsmod.content.skills.sailing.SailingSeaMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        SailingBoats.assertCacheAnchors()
        SailingDocks.assertCacheAnchors()

        boats.assertCacheAnchors()
        seaMap.initialize()

        crew.assertCacheAnchors()

        facilities.assertCacheAnchors()

        PortTaskManager.assertCacheAnchors()

        ShipbuildingXp.assertEveryCorePartPriced()
    }
}
