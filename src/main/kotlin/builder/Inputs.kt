package de.thecommcraft.scratchdsl.builder

interface Input {
    fun represent(): InputRepresentation
}

data class ComposedInput(
    val input: ActualInput,
    val obscuredShadow: ActualInput? = null
) : Input {
    override fun represent(): InputRepresentation {
        val inputType = if (input is ActualShadowInput)
            InputRepresentation.Companion.InputType.SHADOW
        else if (obscuredShadow != null) InputRepresentation.Companion.InputType.OBSCURED_SHADOW
        else InputRepresentation.Companion.InputType.NO_SHADOW
        val inputId = if (input.shouldRepresentById)
            input.representById()
        else null
        val inputBlockRepresentation = if (!input.shouldRepresentById)
            input.representByMinifiedBlockRepresentation()
        else null
        val obscuredShadowId = if (obscuredShadow?.shouldRepresentById == true)
            obscuredShadow.representById()
        else null
        val obscuredShadowBlockRepresentation = if (obscuredShadow?.shouldRepresentById == false)
            obscuredShadow.representByMinifiedBlockRepresentation()
        else null
        return InputRepresentation(
            inputType,
            inputId,
            inputBlockRepresentation,
            obscuredShadowId,
            obscuredShadowBlockRepresentation
        )
    }
}

interface ActualInput {
    val shouldRepresentById: Boolean
    fun representById(): String?
    fun representByMinifiedBlockRepresentation(): MinifiedBlockRepresentation?
}

interface ActualShadowInput : ActualInput

class ActualVarInput(
    val variable: Variable
) : ActualInput {
    override val shouldRepresentById = false
    override fun representById(): String? {
        return null
    }
    override fun representByMinifiedBlockRepresentation(): MinifiedBlockRepresentation.Companion.VariableRepresentation {
        return MinifiedBlockRepresentation.Companion.VariableRepresentation(variable)
    }
}

class ActualListInput(
    val list: ScratchList
) : ActualInput {
    override val shouldRepresentById = false
    override fun representById(): String? {
        return null
    }
    override fun representByMinifiedBlockRepresentation(): MinifiedBlockRepresentation.Companion.ListRepresentation {
        return MinifiedBlockRepresentation.Companion.ListRepresentation(list)
    }
}

class ActualValueInput(
    val value: Value
) : ActualInput {
    override val shouldRepresentById = false
    override fun representById(): String? {
        return null
    }
    override fun representByMinifiedBlockRepresentation(): MinifiedBlockRepresentation {
        value.string?.let {
            return MinifiedBlockRepresentation.Companion.StringRepresentation(it)
        }
        value.int?.let {
            return MinifiedBlockRepresentation.Companion.IntRepresentation(it)
        }
        value.number?.let {
            return MinifiedBlockRepresentation.Companion.NumberRepresentation(it)
        }
        return MinifiedBlockRepresentation.Companion.IntRepresentation(0)
    }
}

class ActualIdExpressionInput(
    val expression: IdExpression
) : ActualInput {
    override val shouldRepresentById = true
    override fun representById(): String {
        return expression.expressionId
    }
    override fun representByMinifiedBlockRepresentation(): MinifiedBlockRepresentation? {
        return null
    }
}

fun ActualInput.independent() = ComposedInput(this, ActualValueInput(Value.ZERO))