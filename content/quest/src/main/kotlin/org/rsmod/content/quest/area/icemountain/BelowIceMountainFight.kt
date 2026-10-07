package org.rsmod.content.quest.area.icemountain

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.SequenceServerType
import org.rsmod.api.config.objParam
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.ScriptContext

internal class BelowIceMountainFight(
    private val quest: Quest,
    private val cutscene: BelowIceMountainCutscene,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val random: GameRandom,
    private val playerHitModifier: PlayerHitModifier,
    private val npcHitModifier: NpcHitModifier,
    private val protectedAccess: ProtectedAccessLauncher,
) {

    private val pillarSwings = quest.temporaryInt("bim_pillar_swings", "varp.below_ice_mountain_pillar_swings")

    private val guardianType: NpcServerType by lazy {
        ServerCacheManager.getNpc(BelowIceMountainRefs.Guardian.asRSCM(RSCMType.NPC))
            ?: error("Missing npc type: ${BelowIceMountainRefs.Guardian}")
    }

    fun ScriptContext.register() {
        onOpLoc1(BelowIceMountainRefs.BossRock) { minePillar(it.loc) }

        onAiOpPlayer2(guardianType) { attackTarget(it.target) }
        onAiApPlayer2(guardianType) { attackTarget(it.target) }

        onNpcQueue(guardianType, "queue.death") { guardianDies() }
    }

    private suspend fun ProtectedAccess.minePillar(pillar: BoundLocInfo) {
        val pickaxe = findPickaxe()
        if (pickaxe == null) {
            mes("You need a pickaxe to mine this pillar.")
            return
        }
        if (player.miningLvl < MiningLevelRequirement) {
            mes("You need a Mining level of $MiningLevelRequirement to mine this pillar.")
            return
        }

        anim(pickaxeAnim(pickaxe))
        spam("You swing your pick at the pillar.")
        delay(SwingTicks)

        if (!landsSwing()) {
            opLoc1(pillar)
            return
        }

        collapsePillar(pillar)
    }

    private fun ProtectedAccess.landsSwing(): Boolean {
        val swings = pillarSwings.get(player) + 1
        pillarSwings.set(player, swings)
        if (swings < MinimumSwings) {
            return false
        }
        return random.of(SwingChanceDenominator) < SwingChanceNumerator
    }

    private suspend fun ProtectedAccess.collapsePillar(pillar: BoundLocInfo) {
        pillarSwings.set(player, 0)

        locRepo.add(
            pillar.coords,
            BelowIceMountainRefs.BossRockMined,
            PillarRemainsTicks,
            pillar.angle,
            pillar.shape,
        )
        soundArea(
            worldRepo,
            player.coords,
            RoofCollapse,
            delay = CollapseSoundDelay,
            radius = ArenaSoundRadius,
        )
        camShake(axis = 0, random = CollapseShakeXz, amplitude = 0, rate = 0)
        camShake(axis = 1, random = CollapseShakeXz, amplitude = 0, rate = 0)
        camShake(axis = 2, random = CollapseShakeY, amplitude = 0, rate = 0)

        statAdvance("stat.mining", PillarXp)

        val guardian = cutscene.activeGuardian(player)
        if (guardian == null || !guardian.isSlotAssigned) {
            return
        }

        delay(1)
        guardian.spotanim(
            RockfallSpotanim,
            delay = GuardianHitSpotanimDelay,
            height = GuardianHitSpotanimHeight,
        )
        delay(1)
        guardian.say("Grrr!")
        delay(1)
        soundArea(worldRepo, guardian.coords, GuardianDefend, radius = ArenaSoundRadius)
        // NPC queues require a positive delay; the player pass queues this for the next NPC tick.
        // TODO: Move the splat from collapse+3 to collapse+2 without changing sound ordering.
        guardian.queueHit(
            source = player,
            delay = 1,
            type = HitType.Melee,
            damage = random.of(PillarDamageMin, PillarDamageMax),
            modifier = npcHitModifier,
        )
    }

    private fun StandardNpcAccess.attackTarget(target: Player) {
        if (actionDelay > mapClock) {
            return
        }
        if (!npc.isInCombat()) {
            resetMode()
            return
        }
        actionDelay = mapClock + GuardianAttackRate

        anim(BelowIceMountainRefs.GuardianAttackSeq)
        target.soundSynth(GuardianMelee)
        target.queueHit(
            npc,
            GuardianHitDelay,
            HitType.Melee,
            random.of(GuardianMinHit, GuardianMaxHit),
            modifier = playerHitModifier,
        )
    }

    private suspend fun StandardNpcAccess.guardianDies() {
        val player = cutscene.guardianOwner(npc)

        hideAllOps()
        noneMode()
        anim(BelowIceMountainRefs.GuardianDeathSeq)
        worldRepo.soundArea(npc.coords, GuardianDeath, radius = ArenaSoundRadius)
        delay(GuardianDeathTicks)

        if (player == null || !player.isSlotAssigned) {
            despawnGuardian()
            return
        }

        val launched =
            protectedAccess.launch(player) {
                quest.setQuestStage(this, BelowIceMountainStage.GuardianSlain)
                cutscene.aftermath(this)
            }
        if (!launched) {
            queueDeath()
            return
        }

        despawnGuardian()
    }

    private fun StandardNpcAccess.despawnGuardian() {
        cutscene.clearGuardian(npc)
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    private fun ProtectedAccess.findPickaxe(): InvObj? {
        val worn = player.righthand?.takeIf { isUsablePickaxe(it) }
        if (worn != null) {
            return worn
        }
        return inv.firstOrNull { it != null && isUsablePickaxe(it) }
    }

    private fun ProtectedAccess.isUsablePickaxe(obj: InvObj): Boolean {
        val type = getInvObj(obj)
        return type.isContentType(BelowIceMountainRefs.MiningPickaxe) &&
            player.miningLvl >= type.pickaxeLevelReq
    }

    private fun pickaxeAnim(pickaxe: InvObj): String {
        val seq = getInvObj(pickaxe).pickaxeAnim
        return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
    }

    private companion object {
        private val ItemServerType.pickaxeLevelReq: Int by objParam(params.levelrequire)
        private val ItemServerType.pickaxeAnim: SequenceServerType by objParam(params.skill_anim)

        private const val MiningLevelRequirement = 10

        private const val SwingTicks = 2

        private const val MinimumSwings = 2
        private const val SwingChanceNumerator = 3
        private const val SwingChanceDenominator = 4

        private const val PillarRemainsTicks = 6000

        private const val RoofCollapse = "synth.ROOF_COLLAPSE"

        private const val CollapseSoundDelay = 35
        private const val ArenaSoundRadius = 10

        private const val CollapseShakeXz = 5

        private const val CollapseShakeY = 7

        private const val PillarXp = 10.0

        private const val RockfallSpotanim = "spotanim.rockfall"

        private const val GuardianHitSpotanimDelay = 20
        private const val GuardianHitSpotanimHeight = 10

        private const val GuardianDefend = "synth.BIM_ANCIENT_GUARDIAN_DEFEND"

        private const val GuardianDeath = "synth.BIM_ANCIENT_GUARDIAN_DEATH"

        private const val PillarDamageMin = 10

        private const val PillarDamageMax = 12

        private const val GuardianAttackRate = 6

        private const val GuardianMelee = "synth.BIM_ANCIENT_GUARDIAN_MELEE_ATTACK"

        // Queues added during NPC processing decrement in the same tick's player queue pass.
        private const val GuardianHitDelay = 1

        private const val GuardianMinHit = 2

        private const val GuardianMaxHit = 5

        private const val GuardianDeathTicks = 5
    }
}
