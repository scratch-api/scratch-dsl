package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

abstract class Expression(opcode: String?) : NormalBlock(opcode) {
    open val independent = true
    open val isShadow = false

    open fun representAlone(): Representation =
        JsonPrimitive(id)

    fun representAsInput(): Representation {
        return buildJsonArray {
            add(if (isShadow) 1 else 2)
            add(representAlone())
        }
    }
}

class NormalUnaryOp(
    opcode: String?,
    expression: Expression?,
    expressionInputName: String = "OPERAND",
    shadowExpression: ShadowExpression? = null
) : Expression(opcode) {
    init {
        if (shadowExpression != null) {
            expressionInputs[expressionInputName] = shadowExpression to expression
        } else {
            shadowlessExpressionInputs[expressionInputName] = expression
        }
    }
}

class NormalBinaryOp(
    opcode: String?,
    expressionA: Expression?,
    expressionB: Expression?,
    expressionAInputName: String = "NUM1",
    expressionBInputName: String = "NUM2",
    shadowExpressionA: ShadowExpression? = null,
    shadowExpressionB: ShadowExpression? = null
) : Expression(opcode) {
    init {
        if (shadowExpressionA != null) {
            expressionInputs[expressionAInputName] = shadowExpressionA to expressionA
        }
        else {
            shadowlessExpressionInputs[expressionAInputName] = expressionA
        }
        if (shadowExpressionB != null) {
            expressionInputs[expressionBInputName] = shadowExpressionB to expressionB
        }
        else {
            shadowlessExpressionInputs[expressionBInputName] = expressionB
        }
    }
}

abstract class ShadowExpression(opcode: String? = null) : Expression(opcode) {
    override val isShadow = true
    abstract override fun representAlone(): Representation
    fun representAsInputWith(other: Expression?): Representation {
        if (other == null) return representAsInput()
        return buildJsonArray {
            add(3)
            add(other.representAlone())
            add(representAlone())
        }
    }
}

data class ValueShadowExpression(val value: String, override val opcode: String? = null) : ShadowExpression(opcode) {
    companion object {
        val EMPTY = ValueShadowExpression("", SpecialInternalInputBlock.TEXT.opcode)
        val ZERO = ValueShadowExpression("0", SpecialInternalInputBlock.NUMBER.opcode)
        enum class SpecialInternalInputBlock(val opcode: String, val numericType: Int) {
            NUMBER("math_number", 4),
            POSITIVE_NUMBER("math_positive_number", 5),
            POSITIVE_INTEGER("math_whole_number", 6),
            INTEGER("math_integer", 7),
            ANGLE("math_angle", 8),
            COLOUR_PICKER("colour_picker", 9),
            TEXT("text", 10);
            fun asExpression(value: String) = ValueShadowExpression(value, opcode)
            fun asExpression(value: JsonPrimitive) =
                if (value.isString) ValueShadowExpression(value.content)
                else ValueShadowExpression(value.toString(), opcode)
        }
    }

    override val independent: Boolean = false
    val numericType = if (opcode != null) {
        when (opcode) {
            "math_number" -> 4
            "math_positive_number" -> 5
            "math_whole_number" -> 6
            "math_integer" -> 7
            "math_angle" -> 8
            "colour_picker" -> 9
            "text" -> 10
            else -> 10
        }
    } else 10
    override fun representAlone(): Representation =
        buildJsonArray {
            add(numericType)
            add(value)
        }
}