package de.thecommcraft.scratchdsl.builder


sealed interface Expression {
    fun asInput(): Input
}

interface IdExpression : Expression {
    val expressionId: String
    override fun asInput(): Input {
        return ActualIdExpressionInput(this).independent()
    }
}

class Value(
    val string: String? = null,
    val int: Int? = null,
    val number: Double? = null
) : Expression {
    companion object {
        val ZERO = Value(int = 0)
    }

    override fun asInput(): Input {
        return ComposedInput(
            ActualValueInput(this)
        )
    }
}

data class Variable(
    val name: String,
    val startValue: Value,
    val variableId: String = randomId()
) : IdExpression {
    override val expressionId = variableId
    override fun asInput(): Input {
        return ActualVarInput(this).independent()
    }
}

data class ScratchList(
    val name: String,
    val startValue: List<Value>,
    val listId: String = randomId()
) : IdExpression {
    override val expressionId = listId
    override fun asInput(): Input {
        return ActualListInput(this).independent()
    }
}

interface BinaryOp : IdExpression {
    val first: Expression
    val second: Expression
}

interface UnaryOp : IdExpression {
    val input: Expression
}
