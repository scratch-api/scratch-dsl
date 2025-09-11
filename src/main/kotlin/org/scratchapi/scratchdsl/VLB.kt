package org.scratchapi.scratchdsl

import kotlinx.serialization.json.*

internal enum class VLBVariant(val numericType: Int) {
    VARIABLE(12),
    LIST(13),
    BROADCAST(11);
}

sealed class VLB(opcode: String?, val name: String, private val variant: VLBVariant) : NormalExpression(opcode), Field {
    override val fieldValue get() = Field.Companion.FieldValue(name, id)

    override val independent = false

    override fun representAlone(): Representation =
        buildJsonArray {
            add(variant.numericType)
            add(name)
            add(id)
        }

    override fun cloneExpression(): Expression {
        return this
    }
}

sealed interface VariableLike {
    val name: String
}

class Variable internal constructor(
    name: String
) : VLB(null, name, VLBVariant.VARIABLE), HandlesSet, VariableLike {

    override var expressionSetHandler: ((Expression?) -> Block)? = { expression ->
        NormalBlock("data_setvariableto")
            .withExpression("VALUE", expression, ValueInput.TEXT.of("0"))
            .withField("VARIABLE", this)
    }

    override var expressionChangeHandler: ((Expression?) -> Block)? = { expression ->
        NormalBlock("data_changevariableby")
            .withExpression("VALUE", expression, ValueInput.NUMBER.of("1"))
            .withField("VARIABLE", this)
    }
}

class VariableSlot internal constructor(
    name: String,
    val value: JsonPrimitive = JsonPrimitive(""),
    val cloud: Boolean = false
) : VLB(null, name, VLBVariant.VARIABLE), HandlesSet, VariableLike {

    override var expressionSetHandler: ((Expression?) -> Block)? = { expression ->
        NormalBlock("data_setvariableto")
            .withExpression("VALUE", expression, ValueInput.TEXT.of("0"))
            .withField("VARIABLE", this)
    }

    override var expressionChangeHandler: ((Expression?) -> Block)? = { expression ->
        NormalBlock("data_changevariableby")
            .withExpression("VALUE", expression, ValueInput.NUMBER.of("1"))
            .withField("VARIABLE", this)
    }
}

class ScratchList internal constructor(name: String) : VLB(null, name, VLBVariant.LIST)

class ScratchListSlot internal constructor(name: String, val value: JsonArray) : VLB(null, name, VLBVariant.LIST) {
    internal constructor(name: String, block: JsonArrayBuilder.() -> Unit) : this(name, buildJsonArray(block))
}

class Broadcast internal constructor(name: String) : VLB(null, name, VLBVariant.BROADCAST), ShadowExpression {
    override fun cloneShadowExpression(): ShadowExpression {
        return this
    }
}

class BroadcastSlot internal constructor(name: String) : VLB(null, name, VLBVariant.BROADCAST), ShadowExpression {
    override fun cloneShadowExpression(): ShadowExpression {
        return this
    }
}