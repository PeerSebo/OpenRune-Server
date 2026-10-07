package org.rsmod.content.raids.theatreofblood

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal class TheatreDawnbringer @Inject constructor(
    private val theatre: TheatreManager,
    private val random: GameRandom,
    private val weapons: WeaponAttackManager,
) : SpecialAttackMap, WeaponMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        check(manager.getSpecialEnergyRequirement(WEAPON) == 350)
        registerMagic(WEAPON, Special())
    }

    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register(WEAPON, Normal())
    }

    internal fun eligible(player: Player, target: Npc): Boolean {
        val run = theatre.run(player) ?: return false
        return TheatreDawnbringerRules.allowed(
            active = theatre.active(run),
            room = run.room,
            phase = run.phase,
            participant = player in theatre.players(run),
            boss = run.boss === target && theatre.run(target) === run,
        )
    }

    private fun ProtectedAccess.reject() {
        weapons.stopCombat(this)
        mes("Dawnbringer can only harm Verzik while her shield is active.")
    }

    private fun ProtectedAccess.cast(target: Npc, attack: CombatAttack.Staff, special: Boolean) {
        weapons.setNextAttackDelay(this, 4)
        anim("seq.human_castwave_staff")
        val suffix = if (special) "_spec" else ""
        spotanim("spotanim.dawnbringer_casting$suffix")
        val projectile = weapons.spawnProjectile(this, target,
            "spotanim.dawnbringer_projectile$suffix", "projanim.magic_spell")
        val (serverDelay, clientDelay) = projectile.durations
        val damage = if (special) random.of(75, 150) else
            weapons.rollStaffMaxHit(this, target, TheatreDawnbringerRules.normalMaxHit(player.magicLvl))
        weapons.playMagicHitFx(this, target, clientDelay, null, 10,
            "spotanim.dawnbringer_impact$suffix", 124, null)
        weapons.giveCombatXp(this, target, attack, damage)
        weapons.queueMagicHit(this, target, damage, clientDelay, serverDelay)
        weapons.continueCombat(this, target)
    }

    private inner class Special : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Staff): Boolean {
            if (!eligible(player, target)) {
                reject()
                return false
            }
            cast(target, attack, special = true)
            return true
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Staff): Boolean {
            reject()
            return false
        }
    }

    private inner class Normal : MagicWeapon {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Staff): Boolean {
            if (eligible(player, target)) cast(target, attack, special = false) else reject()
            return true
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Staff): Boolean {
            reject()
            return true
        }
    }

    companion object {
        private const val WEAPON = "obj.verzik_special_weapon"
    }
}

internal object TheatreDawnbringerRules {
    fun allowed(active: Boolean, room: Int, phase: Int, participant: Boolean, boss: Boolean): Boolean =
        active && room == 5 && phase == 0 && participant && boss

    fun normalMaxHit(magicLevel: Int): Int = ((magicLevel / 3 - 2) / 2).coerceAtLeast(2)
}
