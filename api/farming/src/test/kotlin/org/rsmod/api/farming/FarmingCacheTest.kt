package org.rsmod.api.farming

import dev.openrune.ServerCacheManager

abstract class FarmingCacheTest {
    companion object {
        init {
            ServerCacheManager.init(241).close()
        }
    }
}
