package org.rsmod.api.realm.config.updater

import jakarta.inject.Inject
import java.lang.ref.WeakReference
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.RealmConfig
import org.rsmod.game.entity.PlayerList

public class RealmConfigUpdater
@Inject
constructor(private val realm: Realm, private val playerList: PlayerList) {
    private var writeAccessThread = WeakReference<Thread>(null)
    private var enforceThreadCheck = false

    public fun update(newConfig: RealmConfig) {
        assertWriteAccess()
        realm.updateConfig(newConfig)
        syncGlobalXpRate(newConfig.globalXpRate)
    }

    /**
     * Update online players as well as the realm snapshot; existing players otherwise retain
     * their previous XP multiplier.
     */
    public fun setGlobalXpRate(rate: Double) {
        check(realm.isConfigInitialized) {
            "Cannot set global xp rate before the realm config has loaded."
        }
        update(realm.config.copy(globalXpRate = rate))
    }

    /** Reapply the current global multiplier to online players after a realm update. */
    private fun syncGlobalXpRate(rate: Double) {
        for (player in playerList) {
            player.globalXpRate = rate
        }
    }

    public fun attachWriteThread(thread: Thread) {
        writeAccessThread = WeakReference(thread)
        enforceThreadCheck = true
    }

    public fun allowUnsafeUpdates() {
        enforceThreadCheck = false
    }

    private fun assertWriteAccess() {
        if (!enforceThreadCheck) {
            return
        }
        val expected = writeAccessThread.get()
        if (expected == null) {
            val message = "Write thread has been garbage collected; cannot assert write access."
            throw IllegalStateException(message)
        }

        if (expected != Thread.currentThread()) {
            val message =
                "Realm config update is not permitted from non-write thread. " +
                    "(expected=${expected.name}, actual=${Thread.currentThread().name})"
            throw IllegalStateException(message)
        }
    }
}
