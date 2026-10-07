package org.rsmod.api.player.events.interact

import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.types.ItemServerType
import org.rsmod.events.EventBus
import org.rsmod.game.obj.Obj

public sealed class ObjEvents {
    public sealed class Op(public val obj: Obj) : OpEvent(obj.type.toLong())

    public class Op1(obj: Obj) : Op(obj)

    public class Op2(obj: Obj) : Op(obj)

    public class Op3(obj: Obj) : Op(obj)

    public class Op4(obj: Obj) : Op(obj)

    public class Op5(obj: Obj) : Op(obj)

    public class Op6(obj: Obj) : Op(obj)

    public sealed class Ap(public val obj: Obj) : ApEvent(obj.type.toLong())

    public class Ap1(obj: Obj) : Ap(obj)

    public class Ap2(obj: Obj) : Ap(obj)

    public class Ap3(obj: Obj) : Ap(obj)

    public class Ap4(obj: Obj) : Ap(obj)

    public class Ap5(obj: Obj) : Ap(obj)
}

public sealed class ObjContentEvents {
    public sealed class Op(public val obj: Obj, contentGroup: Int) : OpEvent(contentGroup.toLong())

    public class Op1(obj: Obj, content: Int) : Op(obj, content)

    public class Op2(obj: Obj, content: Int) : Op(obj, content)

    public class Op3(obj: Obj, content: Int) : Op(obj, content)

    public class Op4(obj: Obj, content: Int) : Op(obj, content)

    public class Op5(obj: Obj, content: Int) : Op(obj, content)

    public class Op6(obj: Obj, content: Int) : Op(obj, content)

    public sealed class Ap(public val obj: Obj, contentGroup: Int) : ApEvent(contentGroup.toLong())

    public class Ap1(obj: Obj, content: Int) : Ap(obj, content)

    public class Ap2(obj: Obj, content: Int) : Ap(obj, content)

    public class Ap3(obj: Obj, content: Int) : Ap(obj, content)

    public class Ap4(obj: Obj, content: Int) : Ap(obj, content)

    public class Ap5(obj: Obj, content: Int) : Ap(obj, content)
}

public sealed class ObjDefaultEvents {
    public sealed class Op(public val obj: Obj) : OpDefaultEvent()

    public class Op1(obj: Obj) : Op(obj)

    public class Op2(obj: Obj) : Op(obj)

    public class Op3(obj: Obj) : Op(obj)

    public class Op4(obj: Obj) : Op(obj)

    public class Op5(obj: Obj) : Op(obj)

    public class Op6(obj: Obj) : Op(obj)

    public sealed class Ap(public val obj: Obj) : ApDefaultEvent()

    public class Ap1(obj: Obj) : Ap(obj)

    public class Ap2(obj: Obj) : Ap(obj)

    public class Ap3(obj: Obj) : Ap(obj)

    public class Ap4(obj: Obj) : Ap(obj)

    public class Ap5(obj: Obj) : Ap(obj)
}

public class ObjTEvents {
    public class Op(
        public val obj: Obj,
        public val comsub: Int,
        public val objType: ItemServerType?,
        type: ItemServerType,
        component: ComponentType,
    ) : OpEvent(EventBus.composeLongKey(type.id, component.packed))

    public class Ap(
        public val obj: Obj,
        public val comsub: Int,
        public val objType: ItemServerType?,
        type: ItemServerType,
        component: ComponentType,
    ) : ApEvent(EventBus.composeLongKey(type.id, component.packed))
}

public class ObjTContentEvents {
    public class Op(
        public val obj: Obj,
        public val comsub: Int,
        public val objType: ItemServerType?,
        component: ComponentType,
        content: Int,
    ) : OpEvent(EventBus.composeLongKey(content, component.packed))

    public class Ap(
        public val obj: Obj,
        public val comsub: Int,
        public val objType: ItemServerType?,
        component: ComponentType,
        content: Int,
    ) : ApEvent(EventBus.composeLongKey(content, component.packed))
}

public class ObjTDefaultEvents {
    public class Op(
        public val obj: Obj,
        public val comsub: Int,
        public val objType: ItemServerType?,
        public val type: ItemServerType,
        component: ComponentType,
    ) : OpEvent(component.packed.toLong())

    public class Ap(
        public val obj: Obj,
        public val comsub: Int,
        public val objType: ItemServerType?,
        public val type: ItemServerType,
        component: ComponentType,
    ) : ApEvent(component.packed.toLong())
}

public class ObjUEvents {
    public class Op(
        public val obj: Obj,
        public val invSlot: Int,
        public val objType: ItemServerType,
        type: ItemServerType,
    ) : OpEvent(EventBus.composeLongKey(type.id, objType.id))

    public class Ap(
        public val obj: Obj,
        public val invSlot: Int,
        public val objType: ItemServerType,
        type: ItemServerType,
    ) : ApEvent(EventBus.composeLongKey(type.id, objType.id))
}

public class ObjUContentEvents {
    public class Op(
        public val obj: Obj,
        public val invSlot: Int,
        public val objType: ItemServerType,
        groundContent: Int,
    ) : OpEvent(EventBus.composeLongKey(groundContent, objType.id))

    public class Ap(
        public val obj: Obj,
        public val invSlot: Int,
        public val objType: ItemServerType,
        groundContent: Int,
    ) : ApEvent(EventBus.composeLongKey(groundContent, objType.id))
}

public class ObjUDefaultEvents {
    public class OpType(
        public val obj: Obj,
        public val invSlot: Int,
        public val objType: ItemServerType,
        type: ItemServerType,
    ) : OpEvent(type.id.toLong())

    public class ApType(
        public val obj: Obj,
        public val invSlot: Int,
        public val objType: ItemServerType,
        type: ItemServerType,
    ) : ApEvent(type.id.toLong())

    public class OpContent(
        public val obj: Obj,
        public val invSlot: Int,
        public val objType: ItemServerType,
        groundContent: Int,
    ) : OpEvent(groundContent.toLong())

    public class ApContent(
        public val obj: Obj,
        public val invSlot: Int,
        public val objType: ItemServerType,
        groundContent: Int,
    ) : ApEvent(groundContent.toLong())
}
