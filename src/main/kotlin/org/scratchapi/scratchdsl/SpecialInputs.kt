@file:Suppress("unused")

package org.scratchapi.scratchdsl

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

val SpriteBuilder.specialLocation get() = SpecialLocation.of(name)

val SpriteBuilder.cloneTarget get() = CloneTarget.of(name)

val SpriteBuilder.touchObject get() = TouchObject.of(name)

val SpriteBuilder.distanceObject get() = DistanceObject.of(name)

val SpriteBuilder.propertyTarget get() = PropertyTarget.of(name)

val VariableLike.property get() = Property.of(name)

val KeyboardKey.sensingKey get() = SensingKey.of(this)

abstract class NormalShadowExpressionShouldCopy(opcode: String?) : NormalShadowExpression(opcode), ShadowShouldCopy {
    override fun cloneShadowExpression(): ShadowExpression =
        makeCopy()
}

interface SpecialLocation : ShadowExpression, ShadowShouldCopy {
    companion object {
        val random = of("_random_")
        val default = random
        val mouse = of("_mouse_")
        fun of(target: String, opcode: String? = null): SpecialLocation =
            object : NormalShadowExpressionShouldCopy(null), SpecialLocation, OpcodeSettableShadowExpression {
                override val target = target
                override fun representAlone(): Representation =
                    JsonPrimitive(id)
                override var opcode: String? = opcode
                init {
                    fields["TO"] = Field.of(target)
                }

                override fun makeCopy() = of(this.target, this.opcode)
            }
    }
    val target: String
}

interface SpecialDirection : ShadowExpression, ShadowShouldCopy {
    companion object {
        val random = of("_random_")
        val mouse = of("_mouse_")
        val default = mouse
        fun of(target: String, opcode: String? = null): SpecialDirection =
            object : NormalShadowExpressionShouldCopy(null), SpecialDirection, OpcodeSettableShadowExpression {
                override val target = target
                override fun representAlone(): Representation =
                    JsonPrimitive(id)
                override var opcode: String? = opcode
                init {
                    fields["TO"] = Field.of(target)
                }

                override fun makeCopy() = of(this.target, this.opcode)
            }
    }
    val target: String
}

interface CloneTarget : ShadowExpression, ShadowShouldCopy {
    companion object {
        val myself = of("_myself_")
        fun of(target: String): CloneTarget =
            object : NormalShadowExpressionShouldCopy("control_create_clone_of_menu"), CloneTarget {
                override val target = target
                override fun representAlone(): Representation =
                    JsonPrimitive(id)
                init {
                    fields["CLONE_OPTION"] = Field.of(target)
                }

                override fun makeCopy() = of(this.target)
            }
    }
    val target: String
}

interface TouchObject : ShadowExpression, ShadowShouldCopy {
    companion object {
        val mouse = of("_mouse_")
        val edge = of("_edge_")
        fun of(target: String): TouchObject =
            object : NormalShadowExpressionShouldCopy("sensing_touchingobjectmenu"), TouchObject {
                override val target = target
                override fun representAlone(): Representation =
                    JsonPrimitive(id)
                init {
                    fields["TOUCHINGOBJECTMENU"] = Field.of(target)
                }

                override fun makeCopy() = of(this.target)
            }
    }
    val target: String
}

interface DistanceObject : ShadowExpression, ShadowShouldCopy {
    companion object {
        val mouse = of("_mouse_")
        fun of(target: String): DistanceObject =
            object : NormalShadowExpressionShouldCopy("sensing_distancetomenu"), DistanceObject {
                override val target = target
                override fun representAlone(): Representation =
                    JsonPrimitive(id)
                init {
                    fields["DISTANCETOMENU"] = Field.of(target)
                }

                override fun makeCopy() = of(this.target)
            }
    }
    val target: String
}

interface SensingKey : ShadowExpression, ShadowShouldCopy {
    companion object {
        fun of(key: KeyboardKey): SensingKey =
            object : NormalShadowExpressionShouldCopy("sensing_keyoptions"), SensingKey {
                override val key = key
                override fun representAlone(): Representation =
                    JsonPrimitive(id)
                init {
                    fields["KEY_OPTION"] = Field.of(key.name)
                }

                override fun makeCopy() = of(this.key)
            }
    }
    val key: KeyboardKey
}

interface PropertyTarget : ShadowExpression, ShadowShouldCopy {
    companion object {
        val stage = of("_stage_")
        fun of(target: String): PropertyTarget =
            object : NormalShadowExpressionShouldCopy("sensing_of_object_menu"), PropertyTarget {
                override val target = target
                override fun representAlone(): Representation =
                    JsonPrimitive(id)
                init {
                    fields["OBJECT"] = Field.of(target)
                }

                override fun makeCopy() = of(this.target)
            }
    }
    val target: String
}

interface Property : Field {
    companion object {
        val backdropNumber = of("backdrop #")
        val backdropName = of("backdrop name")
        val xPosition = of("x position")
        val yPosition = of("y position")
        val direction = of("direction")
        val costumeNumber = of("costume #")
        val costumeName = of("costume name")
        val size = of("size")
        val volume = of("volume")
        fun of(target: String): Property =
            object : Field, Property {
                override val target = target
                override val fieldValue: Field.Companion.FieldValue
                    get() = Field.Companion.FieldValue(target)
            }
    }
    val target: String
}

object FirstBackdrop : NormalShadowExpressionShouldCopy("looks_backdrops"), Field {
    private lateinit var backdrop: Backdrop

    override val fieldValue: Field.Companion.FieldValue
        get() = Field.Companion.FieldValue(backdrop.name)

    init {
        fields["BACKDROP"] = this
    }

    override fun prepareRepresent(sprite: Sprite) {
        super.prepareRepresent(sprite)
        backdrop = sprite.root.stage.backdrops[0]
    }

    override fun representAlone(): Representation =
        JsonPrimitive(id)

    override fun makeCopy() =
        backdrop.makeCopy()
}

object FirstSprite : NormalShadowExpressionShouldCopy("looks_costume"), Field {
    private lateinit var costume: Costume

    override val fieldValue: Field.Companion.FieldValue
        get() = Field.Companion.FieldValue(costume.name)

    init {
        fields["COSTUME"] = this
    }

    override fun prepareRepresent(sprite: Sprite) {
        super.prepareRepresent(sprite)
        costume = sprite.costumes.toList()[0].second
    }

    override fun representAlone(): Representation =
        JsonPrimitive(id)

    override fun makeCopy() =
        costume.makeCopy()
}

object FirstSound : NormalShadowExpressionShouldCopy("sound_sounds_menu"), Field {
    private var sound: Sound? = null

    override val fieldValue: Field.Companion.FieldValue
        get() = Field.Companion.FieldValue(sound?.name ?: "")

    init {
        fields["COSTUME"] = this
    }

    override fun prepareRepresent(sprite: Sprite) {
        super.prepareRepresent(sprite)
        sound = sprite.sounds.toList().getOrNull(0)?.second
    }

    override fun representAlone(): Representation =
        JsonPrimitive(id)

    override fun makeCopy() =
        sound?.makeCopy() ?: Sound("", "", "")
}

object FirstBroadcast : NormalShadowExpressionShouldCopy("sound_sounds_menu"), Field {
    private lateinit var broadcast: Broadcast

    override val fieldValue: Field.Companion.FieldValue
        get() = Field.Companion.FieldValue(broadcast.name, broadcast.id)

    override fun prepareRepresent(sprite: Sprite) {
        super.prepareRepresent(sprite)
        broadcast = sprite.broadcasts.toList()[0].second
    }

    override fun representAlone(): Representation =
        JsonPrimitive(id)

    override fun makeCopy() =
        broadcast
}

enum class RotationStyle(val value: String) {
    LEFT_RIGHT("left-right"),
    DONT_ROTATE("don't rotate"),
    ALL_AROUND("all around")
}

enum class LooksEffect(val value: String) {
    COLOR("COLOR"),
    FISHEYE("FISHEYE"),
    WHIRL("WHIRL"),
    PIXELATE("PIXELATE"),
    MOSAIC("MOSAIC"),
    BRIGHTNESS("BRIGHTNESS"),
    GHOST("GHOST")
}

enum class SpecialLayer(val value: String) {
    FRONT("front"),
    BACK("back")
}

enum class LayerDirection(val value: String) {
    FORWARD("forward"),
    BACKWARD("backward")
}

enum class SoundEffect(val value: String) {
    PITCH("PITCH"),
    PAN("PAN")
}

enum class WhenGreaterThanComparedValue(val value: String) {
    LOUDNESS("LOUDNESS"),
    TIMER("TIMER")
}

enum class StopType(val code: String) {
    ALL("all"),
    THIS_SCRIPT("this script"),
    OTHER_SCRIPTS_IN_SPRITE("other scripts in sprite")
}

enum class KeyboardKey(override val key: String) : AnyKeyboardKey {
    A("a"), B("b"), C("c"),
    D("d"), E("e"), F("f"),
    G("g"), H("h"), I("i"),
    J("j"), K("k"), L("l"),
    M("m"), N("n"), O("o"),
    P("p"), Q("q"), R("r"),
    S("s"), T("t"), U("u"),
    V("v"), W("w"), X("x"),
    Y("y"), Z("z"), ZERO("0"),
    ONE("1"), TWO("2"), THREE("3"),
    FOUR("4"), FIVE("5"), SIX("6"),
    SEVEN("7"), EIGHT("8"), NINE("9"),
    SPACE("space"),
    LEFT_ARROW("left arrow"),
    RIGHT_ARROW("right arrow"),
    UP_ARROW("up arrow"),
    DOWN_ARROW("down arrow"),
    ENTER("enter"),
    ANY("any")
}

enum class DragMode(val value: String) {
    DRAGGABLE("draggable"),
    NOT_DRAGGABLE("not draggable")
}

enum class TimeUnit(val value: String) {
    YEAR("YEAR"),
    MONTH("MONTH"),
    DATE("DATE"),
    DAY_OF_WEEK("DAYOFWEEK"),
    HOUR("HOUR"),
    MINUTE("MINUTE"),
    SECOND("SECOND")
}

sealed interface ProcedureArgument : Expression {
    val name: String
    val default: String
    val argumentId: String
    fun cloneProcedureArgument(): ProcedureArgument
}

class ProcedureArgumentStringNumberShadow internal constructor(
    override val name: String,
    override val default: String = "",
    override val argumentId: String = IdGenerator.makeRandomId(6)
) : ProcedureArgument, NormalShadowExpression("argument_reporter_string_number") {

    init {
        fields["VALUE"] = Field.of(name)
    }

    override fun representAlone() =
        JsonPrimitive(id)

    override fun cloneShadowExpression(): ShadowExpression {
        return ProcedureArgumentStringNumberShadow(name, default, argumentId)
    }

    override fun cloneProcedureArgument(): ProcedureArgument {
        return ProcedureArgumentStringNumberShadow(name, default, argumentId)
    }
}

class ProcedureArgumentBooleanShadow internal constructor(
    override val name: String,
    override val default: String = "false",
    override val argumentId: String = IdGenerator.makeRandomId(6)
) : ProcedureArgument, NormalShadowExpression("argument_reporter_boolean") {

    init {
        fields["VALUE"] = Field.of(name)
    }

    override fun representAlone() =
        JsonPrimitive(id)

    override fun cloneShadowExpression(): ShadowExpression {
        return ProcedureArgumentBooleanShadow(name, default, argumentId)
    }

    override fun cloneProcedureArgument(): ProcedureArgument {
        return ProcedureArgumentBooleanShadow(name, default, argumentId)
    }
}

class ProcedureArgumentStringNumber internal constructor(
    override val name: String,
    override val default: String = "",
    override val argumentId: String = IdGenerator.makeRandomId(6)
) : ProcedureArgument, NormalExpression("argument_reporter_string_number"), NonShadowShouldCopy {

    init {
        fields["VALUE"] = Field.of(name)
    }

    override fun makeCopy() =
        ProcedureArgumentStringNumber(name, default, argumentId)

    override fun cloneProcedureArgument(): ProcedureArgument {
        return ProcedureArgumentStringNumber(name, default, argumentId)
    }
}

class ProcedureArgumentBoolean internal constructor(
    override val name: String,
    override val default: String = "false",
    override val argumentId: String = IdGenerator.makeRandomId(6)
) : ProcedureArgument, NormalExpression("argument_reporter_boolean"), NonShadowShouldCopy {

    init {
        fields["VALUE"] = Field.of(name)
    }

    override fun makeCopy() =
        ProcedureArgumentBoolean(name, default, argumentId)

    override fun cloneProcedureArgument(): ProcedureArgument {
        return ProcedureArgumentBoolean(name, default, argumentId)
    }
}

class ProcedurePrototype internal constructor(val proccode: String, val warp: Boolean, val arguments: List<ProcedureArgument>) : NormalExpression("procedures_prototype"), ShadowExpression {
    init {
        shadow = true
        arguments.forEach { argument ->
            shadowlessExpressionInputs[argument.id] = argument
        }
        mutation["tagName"] = JsonPrimitive("mutation")
        mutation["children"] = buildJsonArray {  }
        mutation["proccode"] = JsonPrimitive(proccode)
        mutation["argumentids"] = JsonPrimitive(Json.encodeToString(buildJsonArray {
            arguments.forEach { argument ->
                add(argument.argumentId)
            }
        }))
        mutation["argumentnames"] = JsonPrimitive(Json.encodeToString(buildJsonArray {
            arguments.forEach { argument ->
                add(argument.name)
            }
        }))
        mutation["argumentdefaults"] = JsonPrimitive(Json.encodeToString(buildJsonArray {
            arguments.forEach { argument ->
                add(argument.default)
            }
        }))
        mutation["warp"] = JsonPrimitive(Json.encodeToString(warp))
    }

    override fun cloneShadowExpression(): ShadowExpression {
        return ProcedurePrototype(proccode, warp, arguments.map(ProcedureArgument::cloneProcedureArgument))
    }
}

class Procedure internal constructor(
    val procedurePrototype: ProcedurePrototype
)
