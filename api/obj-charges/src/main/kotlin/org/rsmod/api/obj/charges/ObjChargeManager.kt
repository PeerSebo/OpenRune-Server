package org.rsmod.api.obj.charges

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import kotlin.contracts.contract
import kotlin.math.min
import org.rsmod.api.config.refs.params
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj
import org.rsmod.utils.bits.bitMask
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

public class ObjChargeManager {
    public fun getCharges(obj: InvObj?, internal: String): Int {
        val varobj = ServerCacheManager.getVarObj(internal.asRSCM(RSCMType.VAROBJ)) ?: error("Unable to find varobj: $internal")
        return obj?.vars?.getBits(varobj.bits) ?: 0
    }

    public fun addCharges(
        inventory: Inventory,
        slot: Int,
        add: Int,
        internal: String,
        max: Int,
    ): Charge {
        val varobj = ServerCacheManager.getVarObj(internal.asRSCM(RSCMType.VAROBJ)) ?: error("Unable to find varobj: $internal")
        val chargeRange = 0..varobj.bits.bitMask
        require(max in chargeRange) {
            "`max` charges ($max) must be within range [0..${varobj.bits.bitMask}]. (var=$varobj)"
        }
        val obj = inventory[slot] ?: return Charge.Failure.ObjNotFound
        val curr = getCharges(obj, internal)
        val total = min(max, curr + add)
        if (curr == total) {
            return Charge.Failure.AlreadyFullCharges
        }
        val updatedVar = obj.vars.withBits(varobj.bits, total)
        val added = total - curr

        if (curr == 0) {
            // If the obj currently has no charges, we assume it is a _valid_ uncharged version
            // (i.e., it has the `charged_variant` param defined). If not, we throw an exception.
            // While someone with spawn permissions could technically have a "charged" obj with
            // 0 charges, we enforce strict correctness here: they should spawn the uncharged
            // variant and charge it properly. This helps avoid unintended oversights.
            val charged = getInvObj(obj).paramOrNull(params.charged_variant)
            if (charged == null) {
                val message = "Obj missing `charged_variant` param: $obj (type=${getInvObj(obj)})"
                throw IllegalStateException(message)
            }
            inventory[slot] = InvObj(charged, vars = updatedVar)
            return Charge.Success.AddChangeObj(added = added, total = total, charged = charged)
        }

        inventory[slot] = obj.copy(vars = updatedVar)
        return Charge.Success.AddSameObj(added = added, total = total)
    }

    public fun reduceWornCharges(
        player: Player,
        wearpos: Wearpos,
        internal: String,
        decrement: Int,
    ): Uncharge {
        val obj = player.worn[wearpos.slot] ?: return Uncharge.Failure.ObjNotFound
        val type = getInvObj(obj)

        val uncharged = type.paramOrNull(params.uncharged_variant)
        if (uncharged == null) {
            val message = "Obj missing `uncharged_variant` param: $obj (type=$type)"
            throw IllegalStateException(message)
        }

        val varobj = ServerCacheManager.getVarObj(internal.asRSCM(RSCMType.VAROBJ)) ?: error("Unable to find varobj: $internal")

        val currentCharges = obj.vars.getBits(varobj.bits)
        if (currentCharges < decrement) {
            if (currentCharges == 0) {
                player.worn[wearpos.slot] = InvObj(uncharged, vars = obj.vars)
            }
            return Uncharge.Failure.NotEnoughCharges
        }

        val decrementedCharges = currentCharges - decrement
        val decrementedPackedVars = obj.vars.withBits(varobj.bits, decrementedCharges)

        if (decrementedCharges == 0) {
            player.worn[wearpos.slot] = InvObj(uncharged, vars = decrementedPackedVars)
        } else {
            player.worn[wearpos.slot] = obj.copy(vars = decrementedPackedVars)
        }

        return Uncharge.Success(chargesLeft = decrementedCharges)
    }

    public fun removeAllCharges(inventory: Inventory, slot: Int, internal: String): Int {
        val obj = inventory.getValue(slot)
        val type = getInvObj(obj)

        val varobj = ServerCacheManager.getVarObj(internal.asRSCM(RSCMType.VAROBJ)) ?: error("Unable to find varobj: $internal")

        val uncharged = type.paramOrNull(params.uncharged_variant)
        if (uncharged == null) {
            val message = "Obj missing `uncharged_variant` param: $obj (type=$type)"
            throw IllegalStateException(message)
        }

        val previousCharges = obj.vars.getBits(varobj.bits)

        val resetVarValue = obj.vars.withBits(varobj.bits, 0)
        inventory[slot] = InvObj(uncharged, vars = resetVarValue)

        return previousCharges
    }

    public sealed class Charge {
        public sealed class Success : Charge() {
            public abstract val added: Int
            public abstract val total: Int

            public data class AddSameObj(override val added: Int, override val total: Int) :
                Success()

            public data class AddChangeObj(
                override val added: Int,
                override val total: Int,
                public val charged: ItemServerType,
            ) : Success()
        }

        public sealed class Failure : Charge() {
            public data object ObjNotFound : Failure()

            public data object AlreadyFullCharges : Failure()
        }
    }

    public sealed class Uncharge {
        public data class Success(val chargesLeft: Int) : Uncharge() {
            public val fullyUncharged: Boolean
                get() = chargesLeft == 0
        }

        public sealed class Failure : Uncharge() {
            public data object ObjNotFound : Failure()

            public data object NotEnoughCharges : Failure()
        }
    }

    public companion object {
        // Important: These contracts assume the only valid `Charge` results are `Success` or
        // `Failure`. Do not introduce new base result types. Subclasses of `Success` and `Failure`
        // are fine.
        public fun Charge.isFailure(): Boolean {
            contract {
                returns(true) implies (this@isFailure is Charge.Failure)
                returns(false) implies (this@isFailure is Charge.Success)
            }
            return this is Charge.Failure
        }

        // Important: These contracts assume the only valid `Uncharge` results are `Success` or
        // `Failure`. Do not introduce new base result types. Subclasses of `Success` and `Failure`
        // are fine.
        public fun Uncharge.isFailure(): Boolean {
            contract {
                returns(true) implies (this@isFailure is Uncharge.Failure)
                returns(false) implies (this@isFailure is Uncharge.Success)
            }
            return this is Uncharge.Failure
        }
    }
}
