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