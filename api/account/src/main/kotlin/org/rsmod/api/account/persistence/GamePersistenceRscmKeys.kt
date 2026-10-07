package org.rsmod.api.account.persistence

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

internal object GamePersistenceRscmKeys {
    fun decodeVarpKey(db: String): Int? {
        val t = db.trim()
        val id = if (isPlainIntKey(t)) t.toIntOrNull() else try {
            t.asRSCM(RSCMType.VARP)
        } catch (_: IllegalStateException) {
            null
        }
        return id?.takeIf { it >= 0 && ServerCacheManager.getVarp(it) != null }
    }

    fun encodeVarpKey(varpId: Int): String = RSCM.getReverseMapping(RSCMType.VARP, varpId)

    fun decodeInvTypeKey(db: String): String {
        val t = db.trim()
        if (t.isEmpty()) {
            return t
        }
        if (isPlainIntKey(t)) {
            return RSCM.getReverseMapping(RSCMType.INV, t.toInt())
        }
        return t
    }

    fun encodeInvTypeKey(internalName: String): String = internalName

    fun decodeObjKey(db: String): String {
        val t = db.trim()
        if (t.isEmpty()) {
            return t
        }
        if (isPlainIntKey(t)) {
            return RSCM.getReverseMapping(RSCMType.OBJ, t.toInt())
        }
        return t
    }

    fun encodeObjKey(objId: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, objId)

    private fun isPlainIntKey(t: String): Boolean {
        if (t == "-1") {
            return true
        }
        val body = if (t.startsWith('-')) t.drop(1) else t
        return body.isNotEmpty() && body.all { it.isDigit() }
    }
}
