package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.JsonPrimitive


val randomLocation = SpecialLocation.of("_random_")
val mouseLocation = SpecialLocation.of("_mouse_")

val SpriteBuilder.asSpecialLocation get() = SpecialLocation.of(name)

interface SpecialLocation : ShadowExpression, ShadowShouldCopy {
    companion object {
        fun of(target: String, opcode: String? = null): SpecialLocation =
            object : NormalShadowExpression(null), SpecialLocation, OpcodeSettableShadowExpression {
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
enum class RotationStyle(val value: String) {
    LEFT_RIGHT("left-right"),
    DONT_ROTATE("don't rotate"),
    ALL_AROUND("all around")
}

object FirstBackdrop : NormalShadowExpression("looks_backdrops"), Field, ShadowShouldCopy {
    private lateinit var backdrop: Backdrop

    override val fieldValue: Field.Companion.FieldValue
        get() = Field.Companion.FieldValue(backdrop.name)

    init {
        fields["BACKDROP"] = this
    }

    override fun prepareRepresent(sprite: Sprite) {
        super.prepareRepresent(sprite)
        backdrop = sprite.root.stage.costumes.values.first().asBackdrop()
    }

    override fun representAlone(): Representation =
        JsonPrimitive(id)

    override fun makeCopy() =
        backdrop.makeCopy()
}

object FirstSprite : NormalShadowExpression("looks_costume"), Field, ShadowShouldCopy {
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
