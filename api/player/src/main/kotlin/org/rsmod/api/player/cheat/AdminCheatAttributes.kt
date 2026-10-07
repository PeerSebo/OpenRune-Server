package org.rsmod.api.player.cheat

import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player

public var Player.adminGodMode: Boolean by boolVarp("varp.player_admin_god_mode")

public var Player.adminMaxHit: Boolean by boolVarp("varp.player_admin_max_hit")

public var Player.adminOneHitKill: Boolean by boolVarp("varp.player_admin_one_hit_kill")

public var Player.adminInfiniteRunes: Boolean by boolVarp("varp.player_admin_infinite_runes")

public var Player.adminNoClip: Boolean by boolVarp("varp.player_admin_no_clip")
