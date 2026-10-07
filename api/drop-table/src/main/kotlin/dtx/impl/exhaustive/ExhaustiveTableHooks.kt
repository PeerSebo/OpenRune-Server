package dtx.impl.exhaustive

import dtx.table.TableHooks

public interface ExhaustiveTableHooks<T, R> : TableHooks<T, R>, ExhaustiveRollableHooks<T, R>

internal data class ExhaustiveTableHooksImpl<T, R>(
    val baseTableHooks: TableHooks<T, R>,
    val baseRollableHooks: ExhaustiveRollableHooks<T, R> = ExhaustiveRollableHooksImpl(),
) : ExhaustiveTableHooks<T, R>, TableHooks<T, R> by baseTableHooks {

    override fun onExhaust(target: T) {
        baseRollableHooks.onExhaust(target)
    }

    override fun isExhausted(rollable: ExhaustiveRollable<T, R>): Boolean {
        return baseRollableHooks.isExhausted(rollable)
    }

    override fun resetExhaustible(rollable: ExhaustiveRollable<T, R>) {
        return baseRollableHooks.resetExhaustible(rollable)
    }

    override fun incrementExhaustible(rollable: ExhaustiveRollable<T, R>) {
        return baseRollableHooks.incrementExhaustible(rollable)
    }
}
