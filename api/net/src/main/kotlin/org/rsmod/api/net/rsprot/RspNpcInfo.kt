package org.rsmod.api.net.rsprot

import dev.openrune.types.NpcServerType
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar
import org.rsmod.api.net.rsprot.ext.setFaceTarget
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.npc.OpVisibility
import org.rsmod.game.entity.util.EntityFaceTarget
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark

class RspNpcInfo(val rspAvatar: NpcAvatar) : NpcInfoProtocol {
    private var lifecycleInaccessible = false
    private var staffFollowerHidden = false

    /** A separate output gate so revealing staff cannot undo an NPC death/hide operation. */
    internal fun setStaffFollowerHidden(hidden: Boolean) {
        staffFollowerHidden = hidden
        updateInaccessible()
    }

    private fun updateInaccessible() {
        rspAvatar.setInaccessible(lifecycleInaccessible || staffFollowerHidden)
    }

    override fun setSequence(seq: Int, delay: Int) {
        rspAvatar.extendedInfo.setSequence(seq, delay)
    }

    override fun setSpotanim(spotanim: Int, delay: Int, height: Int, slot: Int) {
        rspAvatar.extendedInfo.setSpotAnim(slot, spotanim, delay, height)
    }

    override fun setSay(text: String) {
        rspAvatar.extendedInfo.setSay(text)
    }

    override fun setName(name: String?) {
        rspAvatar.extendedInfo.setNameChange(name)
    }

    override fun setFacePathingEntity(slot: Int, fallbackAngle: Int) {
        rspAvatar.extendedInfo.setFaceTarget(EntityFaceTarget(slot), fallbackAngle)
    }

    override fun setFaceAngle(angle: Int, instant: Boolean) {
        rspAvatar.extendedInfo.setFaceAngle(angle, instant)
    }

    /**
     * Write facing to the avatar so newly observing clients receive the spawn bearing, not only
     * an extended-info update.
     */
    override fun setDirection(direction: Int) {
        rspAvatar.setDirection(direction)
    }

    override fun setTransmog(type: Int) {
        rspAvatar.setId(type)
        rspAvatar.extendedInfo.setTransmogrification(type)
    }

    override fun resetTransmog(originalType: Int) {
        rspAvatar.setId(originalType)
        rspAvatar.extendedInfo.setTransmogrification(originalType)
    }

    override fun setBaseAnimations(type: NpcServerType) {
        // Missing animations must use the native fallback; 65535 is a literal sequence in this block.
        rspAvatar.extendedInfo.resetBaseAnimationSet()
        rspAvatar.extendedInfo.setBaseAnimationSet(
            readyAnim = type.standAnim.baseAnimationOverride(),
            turnLeftAnim = type.rotateLeftAnim.baseAnimationOverride(),
            turnRightAnim = type.rotateRightAnim.baseAnimationOverride(),
            walkAnim = type.walkAnim.baseAnimationOverride(),
            walkAnimBack = type.rotateBackAnim.baseAnimationOverride(),
            walkAnimLeft = type.walkLeftAnim.baseAnimationOverride(),
            walkAnimRight = type.walkRightAnim.baseAnimationOverride(),
            runAnim = type.runSequence.baseAnimationOverride(),
            runAnimBack = type.runBackSequence.baseAnimationOverride(),
            runAnimLeft = type.runLeftSequence.baseAnimationOverride(),
            runAnimRight = type.runRightSequence.baseAnimationOverride(),
            crawlAnim = type.crawlSequence.baseAnimationOverride(),
            crawlAnimBack = type.crawlBackSequence.baseAnimationOverride(),
            crawlAnimLeft = type.crawlLeftSequence.baseAnimationOverride(),
            crawlAnimRight = type.crawlRightSequence.baseAnimationOverride(),
        )
    }

    private fun Int.baseAnimationOverride(): Int = if (this in 0 until 65535) this else Int.MIN_VALUE

    override fun setBodyModel(model: Int) {
        rspAvatar.extendedInfo.setBodyCustomisation(listOf(model), emptyList(), emptyList())
    }

    override fun setBodyRecolours(recolours: List<Int>) {
        rspAvatar.extendedInfo.setBodyCustomisation(emptyList(), recolours, emptyList())
    }

    override fun resetBodyModel() {
        rspAvatar.extendedInfo.resetBodyCustomisations()
    }

    override fun showHeadbar(headbar: Headbar) {
        rspAvatar.extendedInfo.addHeadBar(
            sourceIndex = if (headbar.isNoSource) -1 else headbar.sourceSlot,
            sourceType = headbar.self,
            otherType = if (headbar.isPrivate) -1 else headbar.public,
            startFill = headbar.startFill,
            endFill = headbar.endFill,
            startTime = headbar.startTime,
            endTime = headbar.endTime,
        )
    }

    override fun removeHeadbar(id: Int) {
        rspAvatar.extendedInfo.removeHeadBar(id)
    }

    override fun showHitmark(hitmark: Hitmark) {
        rspAvatar.extendedInfo.addHitMark(
            sourceIndex = if (hitmark.isNoSource) -1 else hitmark.sourceSlot,
            sourceType = hitmark.source,
            otherType = if (hitmark.isPrivate) -1 else hitmark.public,
            value = hitmark.damage,
            delay = hitmark.delay,
        )
    }

    override fun toggleOps(ops: OpVisibility) {
        rspAvatar.extendedInfo.setVisibleOps(ops.packed.toByte())
    }

    override fun setHeadIcon(slot: Int, graphic: Int, index: Int) {
        rspAvatar.extendedInfo.setHeadIconChange(slot, graphic, index)
    }

    override fun exactMove(
        deltaX1: Int,
        deltaZ1: Int,
        deltaX2: Int,
        deltaZ2: Int,
        delay1: Int,
        delay2: Int,
        direction: Int,
    ) {
        rspAvatar.extendedInfo.setExactMove(
            deltaX1 = deltaX1,
            deltaZ1 = deltaZ1,
            delay1 = delay1,
            deltaX2 = deltaX2,
            deltaZ2 = deltaZ2,
            delay2 = delay2,
            angle = direction,
        )
    }

    override fun crawl(deltaX: Int, deltaZ: Int) {
        rspAvatar.crawl(deltaX, deltaZ)
    }

    override fun walk(deltaX: Int, deltaZ: Int) {
        rspAvatar.walk(deltaX, deltaZ)
    }

    override fun run(deltaX1: Int, deltaZ1: Int, deltaX2: Int, deltaZ2: Int) {
        rspAvatar.walk(deltaX1, deltaZ1)
        rspAvatar.walk(deltaX2, deltaZ2)
    }

    override fun teleport(x: Int, z: Int, level: Int, jump: Boolean) {
        rspAvatar.teleport(level, x, z, jump)
    }

    override fun disable() {
        lifecycleInaccessible = true
        updateInaccessible()
    }

    override fun hide() {
        lifecycleInaccessible = true
        updateInaccessible()
    }

    override fun reveal() {
        lifecycleInaccessible = false
        updateInaccessible()
    }

    override fun isActive(): Boolean {
        return rspAvatar.isActive()
    }
}
