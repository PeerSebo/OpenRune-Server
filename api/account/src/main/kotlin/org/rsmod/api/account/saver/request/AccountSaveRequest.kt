package org.rsmod.api.account.saver.request

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.game.entity.Player

public data class AccountSaveRequest(
    val accountId: Int,
    val characterId: Int,
    val player: Player,
    val callback: AccountSaveCallback,
    var attempts: Int = 0,
) {
    internal var preparedSegments: List<CharacterDataStage.PreparedSave>? = null
    internal var sequence: Long = 0
    internal var saveGeneration: String? = null
    internal var recoverySnapshot: Map<String, Any?>? = null
    internal var atomicGroup: org.rsmod.api.account.saver.AtomicAccountSave? = null
}
