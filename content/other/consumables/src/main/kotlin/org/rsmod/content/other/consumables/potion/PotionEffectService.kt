package org.rsmod.content.other.consumables.potion

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.mechanics.toxins.ToxinImmunity
import org.rsmod.api.mechanics.toxins.impl.PlayerDisease
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

@Singleton
class PotionEffectService
@Inject
constructor(
    private val specialEffects: PotionSpecialEffectService,
    private val antifireEffects: AntifireEffectService,
    private val prayerRegenerationEffects: PrayerRegenerationEffectService,
    private val staminaEffects: StaminaEffectService,
    private val divineEffects: DivineEffectService,
) {
    constructor(
        specialEffects: PotionSpecialEffectService,
        worldClock: MapClock,
        antifireEffects: AntifireEffectService,
        prayerRegenerationEffects: PrayerRegenerationEffectService,
    ) : this(specialEffects, antifireEffects, prayerRegenerationEffects, StaminaEffectService(worldClock), DivineEffectService(worldClock))

    fun processDivineEffects(access: ProtectedAccess) {
        divineEffects.process(access)
    }

    fun processStamina(access: ProtectedAccess) {
        staminaEffects.process(access)
    }

    internal fun onLogin(player: Player) {
        staminaEffects.onLogin(player)
        antifireEffects.onLogin(player)
        prayerRegenerationEffects.onLogin(player)
        divineEffects.onLogin(player)
    }

    internal fun onLogout(player: Player) {
        staminaEffects.onLogout(player)
        antifireEffects.onLogout(player)
        prayerRegenerationEffects.onLogout(player)
        divineEffects.onLogout(player)
    }

    fun supports(
        effect: PotionEffectRow,
    ): Boolean =
        effect.kind != KIND_HANDLER ||
            specialEffects.handles(effect.handler)

    fun canApply(
        access: ProtectedAccess,
        effect: PotionEffectRow,
    ): Boolean =
        with(access) {
            when (effect.kind) {
                KIND_DIVINE -> {
                    if (player.hitpoints <= effect.damage) {
                        mes("You need more Hitpoints to drink this potion.")
                        false
                    } else {
                        effect.baseEffect?.let { canApply(this, it) } ?: false
                    }
                }

                KIND_COMPOUND -> effect.effects.all { canApply(this, it) }

                KIND_HANDLER ->
                    specialEffects.canApply(
                        access = this,
                        effect = effect,
                    )

                else -> true
            }
        }

    fun apply(
        access: ProtectedAccess,
        effect: PotionEffectRow,
    ) {
        applyEffect(access, effect)
    }

    fun healMix(
        access: ProtectedAccess,
        amount: Int,
    ) {
        if (amount <= 0) {
            return
        }

        with(access) {
            restoreIfDrained(
                stat = "stat.hitpoints",
                constant = amount,
                percent = 0,
            )
        }
    }

    private fun applyEffect(
        access: ProtectedAccess,
        effect: PotionEffectRow,
    ) {
        with(access) {
            when (effect.kind) {
                KIND_STAT_BOOST ->
                    effect.skills.forEach { stat ->
                        statBoost(
                            stat = stat.internalName,
                            constant = effect.base,
                            percent = effect.percent,
                        )
                    }

                KIND_FLAT_STAT_BOOST ->
                    effect.skills.forEach { stat ->
                        statBoost(
                            stat = stat.internalName,
                            constant = effect.amount,
                            percent = 0,
                        )
                    }

                KIND_STAT_RESTORE ->
                    restoreStats(effect)

                KIND_PRAYER_RESTORE ->
                    restoreIfDrained(
                        stat = PRAYER,
                        constant = effect.base,
                        percent = effect.percent,
                    )

                KIND_PRAYER_REGENERATION ->
                    prayerRegenerationEffects.apply(this, effect.duration)

                KIND_RUN_ENERGY ->
                    restoreRunEnergy(effect.amount)

                KIND_POISON_CURE -> {
                    if (!PlayerVenom.reduceToPoison(player)) {
                        PlayerPoison.clear(player)
                    }

                    ToxinImmunity.grantImmunity(
                        player = player,
                        poisonDuration =
                            effect.poisonImmunity,
                        venomDuration =
                            effect.venomImmunity,
                    )
                }

                KIND_VENOM_CURE -> {
                    PlayerVenom.clear(player)
                    PlayerPoison.clear(player)

                    ToxinImmunity.grantImmunity(
                        player = player,
                        poisonDuration =
                            effect.poisonImmunity,
                        venomDuration =
                            effect.venomImmunity,
                    )
                }

                KIND_DRAGONFIRE_PROTECTION ->
                    antifireEffects.apply(this, effect)

                KIND_COMPOUND -> {
                    effect.effects.forEach { nested ->
                        applyEffect(
                            access = this,
                            effect = nested,
                        )
                    }

                    if (effect.stamina) {
                        staminaEffects.apply(this, effect.duration)
                    }

                    if (effect.curesDisease) {
                        PlayerDisease.clear(player)
                    }
                }

                KIND_HANDLER -> {
                    specialEffects.apply(
                        access = this,
                        effect = effect,
                    )

                    if (
                        specialEffects
                            .cancelsMaintainedBoosts(effect)
                    ) {
                        divineEffects.cancelMaintenance(player)
                    }
                }

                KIND_DIVINE -> {
                    val baseEffect =
                        requireNotNull(effect.baseEffect) {
                            "Divine potion effect '${effect.key}' " +
                                "has no base effect."
                        }

                    applyEffect(
                        access = this,
                        effect = baseEffect,
                    )

                    takeInstantHit(
                        type = HitType.Typeless,
                        damage = effect.damage,
                    )

                    divineEffects.apply(this, effect)
                }

                else ->
                    error(
                        "Unsupported potion effect kind: " +
                            "'${effect.kind}'.",
                    )
            }
        }
    }

    private fun ProtectedAccess.restoreStats(
        effect: PotionEffectRow,
    ) {
        val excluded =
            effect.excludedSkills.mapTo(
                hashSetOf(),
            ) {
                it.internalName
            }

        ServerCacheManager
            .getStats()
            .values
            .forEach { stat ->
                val internalName =
                    stat.internalName

                if (
                    internalName in excluded ||
                    internalName == "stat.hitpoints"
                ) {
                    return@forEach
                }

                if (
                    internalName == "stat.prayer" &&
                    !effect.restorePrayer
                ) {
                    return@forEach
                }

                restoreIfDrained(
                    stat = internalName,
                    constant = effect.base,
                    percent = effect.percent,
                )
            }
    }

    fun processPrayerRegeneration(access: ProtectedAccess) {
        prayerRegenerationEffects.process(access)
    }

    fun processDragonfireProtection(
        access: ProtectedAccess,
        fullProtection: Boolean,
    ) {
        antifireEffects.process(access, fullProtection)
    }

    companion object {
        const val STAMINA_TIMER: String = StaminaEffectService.TIMER
        const val ANTIFIRE_TIMER: String = AntifireEffectService.ANTIFIRE_TIMER
        const val SUPER_ANTIFIRE_TIMER: String = AntifireEffectService.SUPER_ANTIFIRE_TIMER
        const val DIVINE_TIMER: String = DivineEffectService.DIVINE_TIMER

        const val STAMINA_VARBIT: String = StaminaEffectService.ACTIVE_VARBIT
        const val ANTIFIRE_VARBIT: String = AntifireEffectService.ANTIFIRE_VARBIT
        const val SUPER_ANTIFIRE_VARBIT: String = AntifireEffectService.SUPER_ANTIFIRE_VARBIT

        private const val KIND_STAT_BOOST = "stat_boost"
        private const val KIND_FLAT_STAT_BOOST = "flat_stat_boost"
        private const val KIND_STAT_RESTORE = "stat_restore"
        private const val KIND_PRAYER_RESTORE = "prayer_restore"
        private const val KIND_RUN_ENERGY = "run_energy"
        private const val KIND_POISON_CURE = "poison_cure"
        private const val KIND_VENOM_CURE = "venom_cure"
        private const val KIND_DRAGONFIRE_PROTECTION = "dragonfire_protection"
        private const val KIND_COMPOUND = "compound"
        private const val KIND_HANDLER = "handler"
        private const val KIND_DIVINE = "divine"

        private const val KIND_PRAYER_REGENERATION: String =
            "prayer_regeneration"

        private const val PRAYER: String =
            "stat.prayer"

        const val PRAYER_REGENERATION_TIMER: String =
            PrayerRegenerationEffectService.TIMER

        const val PRAYER_REGENERATION_VARBIT: String =
            PrayerRegenerationEffectService.DISPLAY_VARBIT

        const val STAMINA_DURATION_VARBIT: String = StaminaEffectService.DURATION_VARBIT
    }
}
