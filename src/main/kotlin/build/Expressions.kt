package de.thecommcraft.scratchdsl.build

import kotlinx.serialization.json.*

interface Expression : Block {
    val independent: Boolean

    fun representAlone(): Representation

    fun representAsInput(): Representation {
        return buildJsonArray {
            add(if (this@Expression is ShadowExpression) 1 else 2)
            add(representAlone())
        }
    }
}

abstract class NormalExpression(opcode: String?) : NormalBlock(opcode), Expression {
    override val independent = true

    override fun representAlone(): Representation =
        JsonPrimitive(id)
}

class NormalUnaryOp(
    opcode: String?,
    expression: Expression?,
    expressionInputName: String = "OPERAND",
    shadowExpression: ShadowExpression? = null
) : NormalExpression(opcode) {
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
) : NormalExpression(opcode) {
    init {
        if (shadowExpressionA != null && expressionA !is ShadowExpression) {
            expressionInputs[expressionAInputName] = shadowExpressionA to expressionA
        }
        else {
            shadowlessExpressionInputs[expressionAInputName] = expressionA
        }
        if (shadowExpressionB != null && expressionB !is ShadowExpression) {
            expressionInputs[expressionBInputName] = shadowExpressionB to expressionB
        }
        else {
            shadowlessExpressionInputs[expressionBInputName] = expressionB
        }
    }
}

interface ShadowExpression : Expression {
    fun representAsInputWith(other: Expression?): Representation {
        if (other == null) return representAsInput()
        return buildJsonArray {
            add(3)
            add(other.representAlone())
            add(representAlone())
        }
    }
}

abstract class NormalShadowExpression(opcode: String? = null) : NormalExpression(opcode), ShadowExpression {
    abstract override fun representAlone(): Representation
}

enum class MathOps(val code: String) {
    ABS("abs"),
    FLOOR("floor"),
    CEILING("ceiling"),
    SQRT("sqrt"),
    SIN("sin"),
    COS("cos"),
    TAN("tan"),
    ASIN("asin"),
    ACOS("acos"),
    ATAN("atan"),
    LN("ln"),
    LOG("log"),
    EXP("e ^"),
    POW("10 ^");
    fun of(expression: Expression?) =
        NormalUnaryOp(
            "operator_mathop",
            expression,
            "NUM",
            ValueInput.NUMBER.of("")
        ).withField("OPERATOR", Field.of(code))
}

enum class ValueInput(val opcode: String, val numericType: Int) {
    NUMBER("math_number", 4),
    POSITIVE_NUMBER("math_positive_number", 5),
    POSITIVE_INTEGER("math_whole_number", 6),
    INTEGER("math_integer", 7),
    ANGLE("math_angle", 8),
    COLOUR_PICKER("colour_picker", 9),
    TEXT("text", 10);
    fun of(value: String) = ValueShadowExpression(value, opcode)
    fun of(value: JsonPrimitive) =
        if (value.isString) ValueShadowExpression(value.content)
        else ValueShadowExpression(value.toString(), opcode)
}

data class ValueShadowExpression(val value: String, override val opcode: String? = null) : NormalShadowExpression(opcode) {

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