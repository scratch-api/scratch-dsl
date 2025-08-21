package de.thecommcraft.scratchdsl.builder


data class NotGate(
    override val input: Expression,
) : UnaryOp


data class Rounded(
    override val input: Expression,
) : UnaryOp


data class AbsoluteValue(
    override val input: Expression,
) : UnaryOp


data class Floor(
    override val input: Expression,
) : UnaryOp


data class Ceil(
    override val input: Expression,
) : UnaryOp


data class Sqrt(
    override val input: Expression,
) : UnaryOp


data class Sine(
    override val input: Expression,
) : UnaryOp


data class Cosine(
    override val input: Expression,
) : UnaryOp


data class Tangent(
    override val input: Expression,
) : UnaryOp


data class InvSine(
    override val input: Expression,
) : UnaryOp


data class InvCosine(
    override val input: Expression,
) : UnaryOp


data class InvTangent(
    override val input: Expression,
) : UnaryOp


data class NatLogarithm(
    override val input: Expression,
) : UnaryOp


data class Logarithm(
    override val input: Expression,
) : UnaryOp


data class Exp(
    override val input: Expression,
) : UnaryOp


data class PowTen(
    override val input: Expression,
) : UnaryOp


data class StringLength(
    override val input: Expression,
) : UnaryOp


data class ListLength(
    override val input: Expression,
) : UnaryOp