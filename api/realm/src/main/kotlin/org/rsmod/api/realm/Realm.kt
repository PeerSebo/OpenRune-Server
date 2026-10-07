package org.rsmod.api.realm

import org.rsmod.map.CoordGrid

public class Realm(public val name: String) {
    /**
     * Treat RealmConfig as a replaceable snapshot; retain a local reference when an operation
     * needs a consistent view.
     */
    public val config: RealmConfig
        get() = getConfigOrThrow()

    public val isConfigInitialized: Boolean
        get() = ::mutableConfig.isInitialized

    @Volatile private lateinit var mutableConfig: RealmConfig

    public fun updateConfig(config: RealmConfig) {
        this.mutableConfig = config
    }

    private fun getConfigOrThrow(): RealmConfig {
        check(::mutableConfig.isInitialized) { "Realm config has not been initialized yet." }
        return mutableConfig
    }
}

/**
 * @param baseXpRate The base xp rate assigned to new players during registration.
 * @param globalXpRate The global xp modifier typically used for events like Double XP Weekends.
 *   This is usually compounded with each player's personal xp rate.
 * @param requireRegistration If `true`, players must have a pre-registered account in the database
 *   to log in. If `false`, new accounts will be automatically created on first login.
 * @param autoAssignDisplayNames If `true`, player display names will be automatically assigned
 *   based on their login name.
 */
public data class RealmConfig(
    val id: Int,
    val loginMessage: String?,
    val loginBroadcast: String?,
    val baseXpRate: Double,
    val globalXpRate: Double,
    val spawnCoord: CoordGrid,
    val respawnCoord: CoordGrid,
    val devMode: Boolean,
    val requireRegistration: Boolean,
    val autoAssignDisplayNames: Boolean,
)
