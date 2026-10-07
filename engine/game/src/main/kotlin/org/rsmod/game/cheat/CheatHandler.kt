package org.rsmod.game.cheat

import dev.or2.central.account.Rights

public class CheatHandler(
    public val desc: String?,
    public val action: Cheat.() -> Unit,
    /** The same minimum privilege enforced by the command builder; null means any player. */
    public val requiredRights: Rights? = null,
    public val registrant: ClassLoader? = null,
)
