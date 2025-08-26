package de.thecommcraft.scratchdsl.oldbuilder


data class Addition(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class Multiplication(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class Subtraction(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class Division(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class Modulus(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class RandomBetween(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class Concatenation(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class LessThan(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class GreaterThan(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class Equals(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class AndGate(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class OrGate(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class Contains(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp


data class CharAtIndex(
    override val first: Expression,
    override val second: Expression,
    override val expressionId: String = randomId()
) : BinaryOp