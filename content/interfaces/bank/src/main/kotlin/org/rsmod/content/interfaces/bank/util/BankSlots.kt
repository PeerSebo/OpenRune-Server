package org.rsmod.content.interfaces.bank.util

import org.rsmod.game.inv.Inventory

object BankSlots {
    fun shiftLeadingGapsToTail(target: Inventory, indices: IntRange): GapShiftResult {
        val firstNonNullIndex = indices.firstOrNull { target[it] != null }

        if (firstNonNullIndex == null) {
            val size = (indices.last - indices.first) + 1
            return GapShiftResult(leadingGaps = size, compactSize = 0, trailingGaps = size)
        }

        val firstNullIndex = indices.firstOrNull { target[it] == null }

        if (firstNullIndex == null) {
            return GapShiftResult(leadingGaps = 0, compactSize = 0, trailingGaps = 0)
        }

        if (firstNullIndex == indices.last) {
            val exclusiveSize = indices.last - indices.first
            return GapShiftResult(leadingGaps = 0, compactSize = exclusiveSize, trailingGaps = 1)
        }

        val lastNonNullIndex = indices.last { target[it] != null }

        var writeIndex = indices.first
        for (readIndex in firstNonNullIndex..lastNonNullIndex) {
            if (target[writeIndex] != target[readIndex]) {
                target[writeIndex] = target[readIndex]
            }
            if (writeIndex != readIndex && target[readIndex] != null) {
                target[readIndex] = null
            }
            writeIndex++
        }

        for (index in writeIndex..indices.last) {
            if (target[index] != null) {
                target[index] = null
            }
        }

        val compactSize = writeIndex
        val leadingNullCount = firstNonNullIndex - indices.first
        val trailingNullCount = indices.last - writeIndex + 1
        return GapShiftResult(leadingNullCount, compactSize, trailingNullCount)
    }

    data class GapShiftResult(val leadingGaps: Int, val compactSize: Int, val trailingGaps: Int)
}
